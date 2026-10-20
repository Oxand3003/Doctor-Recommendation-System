package utp.edu.pe.recomendaciones.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import utp.edu.pe.recomendaciones.dto.ChatConversationView;
import utp.edu.pe.recomendaciones.dto.ChatConversationView.State;
import utp.edu.pe.recomendaciones.dto.ChatConversationView.Turn;
import utp.edu.pe.recomendaciones.dto.ChatRecommendationRequest;
import utp.edu.pe.recomendaciones.dto.ChatRecommendationResponse;
import utp.edu.pe.recomendaciones.dto.ChatRecommendationResponse.DoctorRecommendationView;
import utp.edu.pe.recomendaciones.dto.ChatRecommendationResponse.Option;
import utp.edu.pe.recomendaciones.dto.ChatRecommendationResponse.Suggestion;
import utp.edu.pe.recomendaciones.dto.SlotView;
import utp.edu.pe.recomendaciones.model.Establecimiento;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.repository.DistritoRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;

/**
 * Asistente de recomendaciones para pacientes.
 * <p>
 * Los médicos, sedes, precios, calificaciones y horarios salen SIEMPRE de la base de datos. El asistente:
 * <ol>
 *   <li>Empieza pidiendo los síntomas y luego el día/fecha (hora y distrito son opcionales).</li>
 *   <li>Entiende textos mal escritos ({@link AsistenteTexto}, {@link CatalogoSintomas}, {@link InterpreteFechas}).</li>
 *   <li>Orienta hacia una especialidad según los síntomas (no es un diagnóstico).</li>
 *   <li>Ofrece opciones rápidas (orientarme, filtrar especialidad, precios más bajos, mejor calificados).</li>
 *   <li>Recuerda la conversación ({@link AsistenteMemoriaService}) hasta que el paciente inicia una nueva.</li>
 * </ol>
 * La IA externa (si hay clave) solo refuerza la elección de especialidad y redacta un comentario empático;
 * si no responde, todo sigue funcionando con las reglas propias.
 */
@Service
public class ChatRecommendationService {
    private static final Logger log = LoggerFactory.getLogger(ChatRecommendationService.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final int MAX_RESULTS = 12;
    private static final int MAX_TIMES_SHOWN = 4;
    private static final int PUNTAJE_FUERTE = 3;
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT);

    static final String SALUDO = "¡Hola! Soy el asistente de MediCerca. Para empezar, cuéntame qué síntomas o malestar "
            + "tienes (aunque lo escribas con errores, te entiendo). También puedes elegir una opción rápida:";
    private static final String AVISO_ORIENTACION = "Es solo una orientación para buscar médico, no un diagnóstico.";
    private static final String AVISO_URGENCIA = "⚠️ Lo que describes podría ser una urgencia: si es intenso o va empeorando, "
            + "acude a emergencias o llama al 106 (SAMU) sin esperar una cita. ";

    private final MedicoRepository medicos;
    private final DistritoRepository distritos;
    private final DoctorProfileService perfiles;
    private final AsistenteMemoriaService memoria;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient http;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String systemPrompt;

    public ChatRecommendationService(MedicoRepository medicos, DistritoRepository distritos,
                                    DoctorProfileService perfiles, AsistenteMemoriaService memoria,
                                    @Value("${medicerca.ai.api-key:}") String apiKey,
                                    @Value("${medicerca.ai.base-url:https://api.groq.com/openai/v1}") String baseUrl,
                                    @Value("${medicerca.ai.model:openai/gpt-oss-120b}") String model,
                                    @Value("${medicerca.ai.system-prompt:}") String systemPrompt) {
        this.medicos = medicos;
        this.distritos = distritos;
        this.perfiles = perfiles;
        this.memoria = memoria;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
        this.model = model;
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder().requestFactory(factory).build();
        if (this.apiKey.isEmpty()) {
            log.warn("Asistente IA: no hay medicerca.ai.api-key; se usarán solo las reglas internas.");
        }
    }

    // ------------------------------------------------------------------ memoria

    /** Conversación guardada del paciente; si no hay, empieza una con el saludo. */
    public ChatConversationView conversation(String email) {
        return memoria.cargar(email).filter(c -> !c.messages().isEmpty()).orElseGet(() -> {
            State inicial = State.inicial();
            List<Turn> saludo = List.of(new Turn("assistant", SALUDO));
            remember(email, inicial, saludo);
            return new ChatConversationView(saludo, inicial);
        });
    }

    /** "Nueva conversación": la anterior se borra y no se recuerda más. */
    public ChatConversationView newConversation(String email) {
        memoria.olvidar(email);
        return conversation(email);
    }

    // ------------------------------------------------------------------ recomendación

    public ChatRecommendationResponse recommend(ChatRecommendationRequest request) {
        return recommend(request, null);
    }

    public ChatRecommendationResponse recommend(ChatRecommendationRequest request, String email) {
        LocalDate today = LocalDate.now(LIMA);
        if (request.date() != null && request.date().isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha debe ser hoy o posterior.");
        }
        String action = request.action() == null || request.action().isBlank() ? "TEXTO"
                : request.action().trim().toUpperCase(Locale.ROOT);
        boolean talks = request.applyMessageFilters();            // el paciente habló (texto u opción rápida)
        boolean freeText = talks && "TEXTO".equals(action);       // y escribió algo para interpretar

        List<Medico> activeDoctors = medicos.findByUsuarioEstadoOrderByRatingPromedioDesc(EstadoUsuario.ACTIVO);
        List<String> specialties = activeDoctors.stream().map(m -> m.getEspecialidad().getNombre())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        List<String> knownDistricts = distritos.findAll().stream().map(d -> d.getNombre())
                .filter(Objects::nonNull).distinct()
                .sorted(Comparator.comparingInt(String::length).reversed()).toList();

        // Lo que el asistente ya sabía (etapa del flujo, propósito) y la conversación previa para dar contexto a la IA.
        ChatConversationView saved = memoria.cargar(email).orElse(null);
        State before = saved != null ? saved.state() : State.inicial();
        String stage = before.stage() == null ? "MENU" : before.stage();
        String purpose = before.purpose();
        String sort = sortOrNull(request.sort()) != null ? sortOrNull(request.sort())
                : (sortOrNull(before.sort()) != null ? sortOrNull(before.sort()) : "rating");

        String current = request.message().trim();
        String specialty = matchSpecialty(request.specialty(), specialties);
        String district = resolveDistrict(request.district(), freeText ? current : "", knownDistricts);
        LocalDate date = request.date();
        LocalTime time = request.time();

        List<Suggestion> suggestions = new ArrayList<>();
        String intro = "";
        boolean urgent = false;
        boolean understood = false;
        String reply = "";

        switch (action) {
            case "MENU" -> {
                stage = "MENU";
                reply = "Claro. ¿Cómo quieres que te ayude?";
            }
            case "SINTOMAS" -> {
                stage = "SINTOMAS";
                purpose = null;
                specialty = null;
                sort = "rating";
                reply = "Cuéntame qué síntomas o molestias tienes: dónde te duele, desde cuándo y qué tan fuerte. "
                        + "Te oriento hacia la especialidad adecuada. " + AVISO_ORIENTACION;
            }
            case "ESPECIALIDAD" -> {
                stage = "ELEGIR";
                purpose = "FILTRO";
                reply = "¿De qué especialidad quieres ver médicos? Elige una de la lista o escríbela.";
            }
            case "PRECIOS" -> {
                stage = "ELEGIR";
                purpose = "PRECIO";
                sort = "price";
                reply = "Te muestro las consultas más económicas. ¿De qué especialidad? Elige una o escríbela.";
            }
            case "CALIFICADOS" -> {
                stage = "ELEGIR";
                purpose = "CALIFICACION";
                sort = "rating";
                reply = "Te muestro los médicos mejor calificados. ¿De qué especialidad? Elige una o escríbela.";
            }
            case "ELEGIR" -> {
                stage = "LIBRE";
                understood = true;
                intro = specialty == null ? "Perfecto, te muestro todas las especialidades. "
                        : "Perfecto, busco médicos de " + specialty + ". ";
            }
            case "ORDEN" -> {
                stage = "LIBRE";
                understood = true;
                intro = "price".equals(sort) ? "Listo, ordené los médicos de menor a mayor precio de consulta. "
                        : "Listo, ordené los médicos de mejor a menor calificación. ";
            }
            default -> {
                if (freeText) {
                    String wantedSort = sortFromText(current);
                    if (wantedSort != null) { sort = wantedSort; understood = true; }
                    LocalDate spokenDate = InterpreteFechas.fecha(current, today);
                    if (spokenDate != null) { date = spokenDate; understood = true; }
                    LocalTime spokenTime = InterpreteFechas.hora(current);
                    if (spokenTime != null) { time = spokenTime; understood = true; }
                    if (district != null && !normalize(district).equals(normalize(request.district()))) understood = true;

                    urgent = CatalogoSintomas.pareceUrgente(current);
                    String byName = CatalogoSintomas.porNombre(current, specialties);
                    if (byName != null) {
                        specialty = byName;
                        understood = true;
                        intro = "Perfecto, busco médicos de " + byName + ". ";
                        stage = "LIBRE";
                    } else {
                        Orientation o = orient(current, specialties, specialty, stage, saved, request);
                        if (o.specialty() != null) {
                            specialty = o.specialty();
                            suggestions = o.suggestions();
                            intro = o.intro();
                            understood = true;
                            stage = "LIBRE";
                        }
                    }
                } else if (talks) {
                    understood = true;
                }
            }
        }
        if (!talks && specialty != null && "ELEGIR".equals(stage)) stage = "LIBRE";   // eligió en el filtro manual

        // 3) Médicos reales de la base de datos filtrados con lo que ya se conoce.
        List<DoctorRecommendationView> recommendations = new ArrayList<>();
        for (Medico doctor : activeDoctors) {
            String doctorSpecialty = doctor.getEspecialidad().getNombre();
            if (specialty != null && !normalize(doctorSpecialty).equals(normalize(specialty))) continue;
            Establecimiento clinic = doctor.getEstablecimiento();
            String clinicDistrict = clinic != null && clinic.getDistrito() != null ? clinic.getDistrito().getNombre() : null;
            if (district != null && (clinicDistrict == null || !normalize(clinicDistrict).equals(normalize(district)))) continue;

            List<String> freeTimes = List.of();
            if (date != null) {
                final LocalTime from = time;
                freeTimes = perfiles.getSlots(doctor.getId(), date, null).stream()
                        .filter(SlotView::disponible)
                        .map(SlotView::hora)
                        .filter(hour -> from == null || !LocalTime.parse(hour).isBefore(from))
                        .toList();
                if (freeTimes.isEmpty()) continue;
            }
            BigDecimal price = lowestPrice(doctor);
            recommendations.add(new DoctorRecommendationView(doctor.getId(),
                    DoctorProfileService.nombrePublico(doctor.getUsuario()), doctorSpecialty,
                    doctor.getRatingPromedio(), doctor.getTotalValoraciones(), doctor.getAniosExperiencia(),
                    clinic != null ? clinic.getNombre() : "Por confirmar", clinicDistrict,
                    date != null, availabilityText(date, time, freeTimes), freeTimes,
                    price == null ? null : price.doubleValue(), priceText(price)));
        }
        recommendations.sort(comparator(sort));
        if (recommendations.size() > MAX_RESULTS) {
            recommendations = new ArrayList<>(recommendations.subList(0, MAX_RESULTS));
        }

        // 4) Qué sigue en la conversación: pedir lo que falta (síntomas -> día/fecha) y botones de respuesta rápida.
        boolean askSymptoms = specialty == null && !"LIBRE".equals(stage);
        List<String> missing = new ArrayList<>();
        if (askSymptoms) missing.add("síntomas o especialidad");
        if (date == null) missing.add("día o fecha (por ejemplo mañana, el viernes o 25/10)");
        boolean complete = specialty != null && date != null;

        List<Option> options = optionsFor(stage, purpose, specialty, date, sort, specialties, suggestions,
                !recommendations.isEmpty(), askSymptoms);

        if (talks) {
            StringBuilder text = new StringBuilder();
            if (urgent) text.append(AVISO_URGENCIA);
            if (!reply.isEmpty()) {
                text.append(reply);
            } else {
                text.append(intro);
                if (!understood) {
                    text.append("No logré entender bien eso. Cuéntame tus síntomas con tus palabras (por ejemplo: "
                            + "\"me duele la cabeza y estoy mareado\") o elige una opción rápida.");
                } else {
                    text.append(nextStep(specialty, district, date, time, sort, askSymptoms, recommendations.size(),
                            "price".equals(sort)));
                }
            }
            reply = text.toString().trim();
        }

        State after = new State(specialty, district, date, time, sort, stage, purpose);
        if (talks && email != null) {
            List<Turn> turns = new ArrayList<>();
            turns.add(new Turn("user", current));
            turns.add(new Turn("assistant", reply));
            remember(email, after, turns);
        } else if (email != null && saved != null) {
            remember(email, after, List.of());                     // solo recuerda los filtros
        }

        return new ChatRecommendationResponse(reply, specialty, district, date, time, complete, missing, specialties,
                recommendations, sort, stage, options, suggestions, urgent);
    }

    /** Guardar la memoria nunca debe tumbar la búsqueda (el error de la base puede saltar al cerrar la transacción). */
    private void remember(String email, State state, List<Turn> turns) {
        try {
            memoria.guardar(email, state, turns);
        } catch (Exception ex) {
            log.warn("No se pudo guardar la memoria del asistente: {}", ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ orientación por síntomas

    private record Orientation(String specialty, String intro, List<Suggestion> suggestions) { }

    /** Elige la especialidad según lo escrito: reglas tolerantes a errores y, si hace falta, la IA. */
    private Orientation orient(String current, List<String> specialties, String currentSpecialty, String stage,
                               ChatConversationView saved, ChatRecommendationRequest request) {
        List<CatalogoSintomas.Sugerencia> rules = CatalogoSintomas.sugerir(current, specialties);
        CatalogoSintomas.Sugerencia top = rules.isEmpty() ? null : rules.get(0);
        boolean strong = top != null && top.puntaje() >= PUNTAJE_FUERTE;

        String aiSpecialty = null;
        String aiComment = null;
        List<String> aiAlternatives = List.of();
        boolean needsAi = !strong && StringUtils.hasText(apiKey)
                && (currentSpecialty == null || "SINTOMAS".equals(stage)) && current.replaceAll("[^\\p{L}]", "").length() >= 4;
        if (needsAi) {
            JsonNode ai = askModel(conversationContext(request, saved), specialties);
            if (ai != null) {
                aiSpecialty = matchSpecialty(ai.path("specialty").asText(null), specialties);
                aiComment = ai.path("comment").isTextual() ? ai.path("comment").asText().trim() : null;
                List<String> alts = new ArrayList<>();
                for (JsonNode n : ai.path("alternatives")) {
                    String m = matchSpecialty(n.asText(null), specialties);
                    if (m != null && !m.equals(aiSpecialty)) alts.add(m);
                }
                aiAlternatives = alts;
            }
        }

        String chosen = strong ? top.especialidad() : (aiSpecialty != null ? aiSpecialty : (top != null ? top.especialidad() : null));
        if (chosen == null) return new Orientation(null, "", List.of());

        List<Suggestion> list = new ArrayList<>();
        String finalChosen = chosen;
        List<String> motives = rules.stream().filter(s -> s.especialidad().equals(finalChosen))
                .flatMap(s -> s.motivos().stream()).toList();
        list.add(new Suggestion(chosen, motives.isEmpty() ? "según lo que describes" : "mencionaste: " + String.join(", ", motives)));
        for (CatalogoSintomas.Sugerencia s : rules) {
            if (list.size() >= 3) break;
            if (!s.especialidad().equals(chosen) && s.puntaje() >= 2) {
                list.add(new Suggestion(s.especialidad(), s.motivos().isEmpty() ? "también podría encajar"
                        : "mencionaste: " + String.join(", ", s.motivos())));
            }
        }
        for (String alt : aiAlternatives) {
            if (list.size() >= 3) break;
            if (list.stream().noneMatch(s -> s.specialty().equals(alt))) list.add(new Suggestion(alt, "también podría encajar"));
        }

        StringBuilder intro = new StringBuilder();
        if (StringUtils.hasText(aiComment) && aiSpecialty != null) intro.append(aiComment.trim()).append(' ');
        intro.append(motives.isEmpty() ? "Por lo que me cuentas, " : "Por lo que me cuentas (" + String.join(", ", motives) + "), ")
                .append("te oriento a ").append(chosen).append(". ").append(AVISO_ORIENTACION).append(' ');
        if (list.size() > 1) {
            intro.append("Otras opciones que podrían encajar: ")
                    .append(String.join(", ", list.stream().skip(1).map(Suggestion::specialty).toList())).append(". ");
        }
        return new Orientation(chosen, intro.toString(), list);
    }

    // ------------------------------------------------------------------ textos y opciones

    private String nextStep(String specialty, String district, LocalDate date, LocalTime time, String sort,
                            boolean askSymptoms, int count, boolean byPrice) {
        if (askSymptoms) {
            return "¿Qué síntomas o molestias tienes? Cuéntame dónde te duele y desde cuándo, o elige una opción.";
        }
        String what = specialty != null ? "de " + specialty : "de todas las especialidades";
        String order = byPrice ? " con la consulta más económica primero" : "";
        if (date == null) {
            String shown = count == 0 ? "Aún no hay médicos " + what + " con esos datos. "
                    : "Estos son los médicos " + what + order + ". ";
            return shown + "¿Para qué día te gustaría la cita? Puedes escribir \"mañana\", \"el viernes\" o una fecha como 25/10.";
        }
        if (count == 0) {
            return "No encontré médicos " + what + " con horario libre el " + date.format(DIA)
                    + (time != null ? " desde las " + time : "") + (district != null ? " en " + district : "")
                    + ". Prueba otro día, otra hora" + (district != null ? " o quita el distrito" : "") + ".";
        }
        return "Estos son los médicos " + what + " con horario libre el " + date.format(DIA) + order + ". "
                + (time == null ? "Si prefieres una hora o un distrito, solo escríbelo." : "Puedes cambiar la hora o el distrito cuando quieras.");
    }

    private List<Option> optionsFor(String stage, String purpose, String specialty, LocalDate date, String sort,
                                    List<String> specialties, List<Suggestion> suggestions, boolean hasResults,
                                    boolean askSymptoms) {
        List<Option> options = new ArrayList<>();
        switch (stage) {
            case "ELEGIR" -> {
                for (String s : specialties) options.add(new Option(s, "ELEGIR", s));
                options.add(new Option("Todas las especialidades", "ELEGIR", ""));
                options.add(new Option("← Volver al menú", "MENU", ""));
            }
            case "SINTOMAS" -> {
                options.add(new Option("Prefiero elegir una especialidad", "ESPECIALIDAD", ""));
                options.add(new Option("← Volver al menú", "MENU", ""));
            }
            case "LIBRE" -> {
                if (date == null) {
                    options.add(new Option("Hoy", null, "hoy"));
                    options.add(new Option("Mañana", null, "mañana"));
                    options.add(new Option("Pasado mañana", null, "pasado mañana"));
                }
                for (Suggestion s : suggestions.stream().skip(1).toList()) {
                    options.add(new Option("Prefiero " + s.specialty(), "ELEGIR", s.specialty()));
                }
                if (hasResults) {
                    if (!"price".equals(sort)) options.add(new Option("Menor precio primero", "ORDEN", "price"));
                    if (!"rating".equals(sort)) options.add(new Option("Mejor calificados primero", "ORDEN", "rating"));
                }
                options.add(new Option("Cambiar especialidad", "ESPECIALIDAD", ""));
                options.add(new Option("Menú de opciones", "MENU", ""));
            }
            default -> options.addAll(menuOptions());
        }
        return options;
    }

    private List<Option> menuOptions() {
        return List.of(
                new Option("🩺 Orientarme según mis síntomas", "SINTOMAS", ""),
                new Option("Filtrar por una especialidad", "ESPECIALIDAD", ""),
                new Option("💲 Precios más bajos por especialidad", "PRECIOS", ""),
                new Option("⭐ Mejor calificados por especialidad", "CALIFICADOS", ""),
                new Option("✍️ Escribir mi propia consulta", "ESCRIBIR", ""));
    }

    private String availabilityText(LocalDate date, LocalTime time, List<String> freeTimes) {
        if (date == null) return "Indica fecha y hora para ver horarios";
        String shown = String.join(", ", freeTimes.stream().limit(MAX_TIMES_SHOWN).toList());
        String more = freeTimes.size() > MAX_TIMES_SHOWN ? " y " + (freeTimes.size() - MAX_TIMES_SHOWN) + " más" : "";
        return "Libre el " + date.format(DIA) + (time != null ? " desde las " + time : "") + ": " + shown + more;
    }

    // ------------------------------------------------------------------ precio y orden

    /** Precio de consulta más bajo que ofrece el médico (presencial o teleconsulta); null si no definió ninguno. */
    private BigDecimal lowestPrice(Medico doctor) {
        BigDecimal in = doctor.getCostoConsulta();
        BigDecimal tele = doctor.getCostoTeleconsulta();
        if (in == null) return tele;
        if (tele == null) return in;
        return in.min(tele);
    }

    private String priceText(BigDecimal price) {
        if (price == null) return "Precio por confirmar";
        BigDecimal p = price.stripTrailingZeros();
        return "S/ " + (p.scale() <= 0 ? p.toBigInteger().toString() : p.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
    }

    private Comparator<DoctorRecommendationView> comparator(String sort) {
        Comparator<DoctorRecommendationView> byRating = Comparator
                .comparing(DoctorRecommendationView::rating, Comparator.reverseOrder())
                .thenComparing(DoctorRecommendationView::reviews, Comparator.reverseOrder());
        if (!"price".equals(sort)) return byRating;
        return Comparator.comparing(DoctorRecommendationView::price, Comparator.nullsLast(Comparator.<Double>naturalOrder()))
                .thenComparing(byRating);
    }

    private String sortOrNull(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase(Locale.ROOT);
        return "price".equals(v) || "rating".equals(v) ? v : null;
    }

    /** "el más barato", "económico", "menor precio" -> price; "mejor calificado", "mejores", "estrellas" -> rating. */
    private String sortFromText(String text) {
        List<String> words = AsistenteTexto.palabras(text);
        for (String t : List.of("barato", "baratos", "barata", "economico", "economicos", "economica", "precio", "precios",
                "costo", "menor precio", "mas bajo", "mas bajos", "menos caro", "mas accesible", "tarifa")) {
            if (AsistenteTexto.contiene(words, t)) return "price";
        }
        for (String t : List.of("mejor calificado", "mejores calificados", "mejor calificada", "mejor valorado", "mejores",
                "calificacion", "calificaciones", "estrellas", "recomendado", "recomendados", "mejor puntuado", "mas recomendado")) {
            if (AsistenteTexto.contiene(words, t)) return "rating";
        }
        return null;
    }

    // ------------------------------------------------------------------ IA

    /** Un solo llamado: {"specialty", "alternatives", "comment"} o null si la IA no está disponible. */
    private JsonNode askModel(String conversation, List<String> specialties) {
        if (!StringUtils.hasText(apiKey)) return null;
        try {
            String instructions = systemPrompt + " Reglas obligatorias: responde en español. Eres un orientador de "
                    + "búsqueda, no un profesional clínico: no diagnostiques, no prescribas ni interpretes gravedad. El "
                    + "paciente puede escribir con faltas de ortografía, abreviaturas o lenguaje coloquial (por ejemplo "
                    + "\"cavesa\", \"me duele la barriga\"): interprétalo con tolerancia. Catálogo de especialidades (usa un "
                    + "nombre EXACTO del catálogo o null): " + mapper.writeValueAsString(specialties)
                    + ". Tareas: 1) elige la especialidad que corresponde a los síntomas o al motivo del paciente (si "
                    + "cambió de tema, la nueva) o null si no es clara; 2) opcionalmente hasta 2 alternativas del catálogo; "
                    + "3) escribe un comentario empático de UNA frase corta, sin preguntas y sin dar diagnóstico (el sistema "
                    + "pide después el día y muestra los médicos). Devuelve únicamente JSON: "
                    + "{\"specialty\": string|null, \"alternatives\": [string], \"comment\": string}.";
            return callModel(instructions, conversation);
        } catch (Exception ex) {
            log.warn("Asistente IA no disponible ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
            return null;
        }
    }

    private JsonNode callModel(String instructions, String message) throws Exception {
        Map<String, Object> body = Map.of("model", model,
                "messages", List.of(Map.of("role", "system", "content", instructions),
                        Map.of("role", "user", "content", message)),
                "response_format", Map.of("type", "json_object"), "temperature", 0.2);
        String endpoint = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        // Se envía y se lee como texto plano para no depender de los convertidores JSON de Spring.
        String raw = http.post().uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(mapper.writeValueAsString(body))
                .retrieve().body(String.class);
        if (!StringUtils.hasText(raw)) return null;
        String content = mapper.readTree(raw).path("choices").path(0).path("message").path("content").asText("");
        content = content.replaceAll("(?s)^\\s*```(?:json)?\\s*|\\s*```\\s*$", "").trim();
        return content.isEmpty() ? null : mapper.readTree(content);
    }

    /** Conversación reciente (la guardada en el servidor o, si no hay, la que envió el navegador) + mensaje actual. */
    private String conversationContext(ChatRecommendationRequest request, ChatConversationView saved) {
        List<Turn> turns = new ArrayList<>();
        if (saved != null && !saved.messages().isEmpty()) {
            turns.addAll(saved.messages());
        } else if (request.history() != null) {
            request.history().forEach(t -> { if (t != null) turns.add(new Turn(t.role(), t.content())); });
        }
        StringBuilder context = new StringBuilder();
        turns.stream().skip(Math.max(0, turns.size() - 8)).forEach(turn -> {
            if (turn == null || !StringUtils.hasText(turn.content())) return;
            String role = "assistant".equalsIgnoreCase(turn.role()) ? "Asistente" : "Paciente";
            context.append(role).append(": ").append(turn.content().trim()).append('\n');
        });
        context.append("Paciente: ").append(request.message().trim());
        return context.toString();
    }

    // ------------------------------------------------------------------ nombres y distritos

    private String matchSpecialty(String value, List<String> specialties) {
        if (!StringUtils.hasText(value)) return null;
        String wanted = normalize(value);
        return specialties.stream().filter(s -> normalize(s).equals(wanted)).findFirst().orElse(null);
    }

    /** Distrito mencionado en el mensaje (tolerando errores); si no, el elegido en el filtro. */
    private String resolveDistrict(String explicit, String current, List<String> known) {
        String fromCurrent = findDistrictIn(current, known);
        if (fromCurrent != null) return fromCurrent;
        if (StringUtils.hasText(explicit)) {
            String typed = normalize(explicit);
            for (String d : known) if (normalize(d).equals(typed)) return d;
            return findDistrictIn(explicit, known);
        }
        return null;
    }

    private String findDistrictIn(String text, List<String> known) {
        if (!StringUtils.hasText(text)) return null;
        // "por la noche" no debe confundirse con el distrito Moche.
        String cleaned = AsistenteTexto.normalizar(text).replaceAll("\\b(la|de la|por la|en la|a la) (manana|tarde|noche)\\b", " ");
        List<String> words = AsistenteTexto.palabras(cleaned);
        if (words.isEmpty()) return null;
        for (String d : known) {                                       // nombre completo (más largos primero)
            if (AsistenteTexto.buscar(words, AsistenteTexto.palabras(d)) >= 0) return d;
        }
        for (String d : known) {                                       // "larco" para Victor Larco Herrera, "porvenir"...
            List<String> parts = AsistenteTexto.palabras(d);
            if (parts.size() < 2) continue;
            for (String part : parts) {
                if (part.length() >= 5 && AsistenteTexto.buscar(words, List.of(part)) >= 0) return d;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return AsistenteTexto.normalizar(value);
    }
}
