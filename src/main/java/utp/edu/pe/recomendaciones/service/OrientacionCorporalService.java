package utp.edu.pe.recomendaciones.service;



import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

import utp.edu.pe.recomendaciones.dto.OrientacionCorporalRequest;
import utp.edu.pe.recomendaciones.dto.OrientacionCorporalResponse;
import utp.edu.pe.recomendaciones.dto.OrientacionCorporalResponse.Alternative;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;

/**
 * Orientación por zona corporal del asistente de IA (maniquí).
 * <p>
 * Dado el sexo del paciente y sus síntomas (o la zona que tocó en el maniquí) devuelve: la zona del cuerpo que
 * hay que marcar, la especialidad sugerida (siempre una que existe en la base) y una explicación de por qué. La
 * zona y la especialidad salen de reglas propias ({@link ZonaCorporal}, {@link CatalogoSintomas}); la IA externa
 * (si hay clave) solo redacta la explicación ampliada. Si no responde, se devuelve la explicación por reglas.
 * Es una orientación para buscar médico, nunca un diagnóstico.
 */
@Service
public class OrientacionCorporalService {
    private static final Logger log = LoggerFactory.getLogger(OrientacionCorporalService.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);

    static final String AVISO = "Esta orientación se genera de forma automática para ayudarte a elegir un especialista. "
            + "No es un diagnóstico médico ni reemplaza una consulta. En una urgencia llama al 106 (SAMU) o acude a emergencias.";

    private static final List<String> CUIDADOS = List.of(
            "Anota desde cuándo tienes el malestar, qué lo mejora y qué lo empeora.",
            "Lleva a tu cita tus exámenes anteriores y la lista de lo que tomas habitualmente.",
            "Evita automedicarte: el especialista te indicará el tratamiento adecuado.");

    private static final List<String> ALARMAS = List.of(
            "Dolor muy intenso o que empeora rápidamente.",
            "Dificultad para respirar, desmayo, confusión o pérdida de fuerza en el cuerpo.",
            "Fiebre alta que no cede o sangrado abundante.");

    private final MedicoRepository medicos;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient http;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String systemPrompt;

    public OrientacionCorporalService(MedicoRepository medicos,
                                      @Value("${medicerca.ai.api-key:}") String apiKey,
                                      @Value("${medicerca.ai.base-url:https://api.groq.com/openai/v1}") String baseUrl,
                                      @Value("${medicerca.ai.model:openai/gpt-oss-120b}") String model,
                                      @Value("${medicerca.ai.system-prompt:}") String systemPrompt) {
        this.medicos = medicos;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
        this.model = model;
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public OrientacionCorporalResponse orientar(OrientacionCorporalRequest request) {
        String sexo = ZonaCorporal.normalizarSexo(request.sex());
        if (sexo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica si eres mujer u hombre.");
        }
        String sexoTexto = "F".equals(sexo) ? "mujer" : "hombre";
        String sexoEtiqueta = "F".equals(sexo) ? "Mujer" : "Hombre";
        String texto = request.symptoms() == null ? "" : request.symptoms().trim();
        List<String> especialidades = especialidadesActivas();

        // 1) Zona del maniquí y especialidad: la tocada por el paciente o la deducida de sus síntomas.
        boolean tocada = StringUtils.hasText(request.zone());
        String zona;
        String especialidad;
        if (tocada) {
            zona = request.zone().trim().toLowerCase(Locale.ROOT);
            if (!ZonaCorporal.existe(zona)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La zona elegida no es válida.");
            }
            especialidad = ZonaCorporal.especialidadParaZona(zona, sexo, especialidades);
        } else {
            especialidad = coincidir(request.specialty(), especialidades);
            if (especialidad == null) {
                List<CatalogoSintomas.Sugerencia> reglas = CatalogoSintomas.sugerir(texto, especialidades);
                if (!reglas.isEmpty()) especialidad = reglas.get(0).especialidad();
            }
            zona = ZonaCorporal.detectar(texto, especialidad);
        }
        String etiqueta = ZonaCorporal.etiqueta(zona);
        List<String> motivos = ZonaCorporal.motivos(texto, zona);
        boolean urgente = CatalogoSintomas.pareceUrgente(texto);

        List<Alternative> alternativas = new ArrayList<>();
        for (CatalogoSintomas.Sugerencia s : CatalogoSintomas.sugerir(texto, especialidades)) {
            if (alternativas.size() >= 2) break;
            if (s.especialidad().equals(especialidad) || s.puntaje() < 2) continue;
            alternativas.add(new Alternative(s.especialidad(), s.motivos().isEmpty()
                    ? "también podría encajar" : "mencionaste: " + String.join(", ", s.motivos())));
        }

        // 2) Explicación por reglas (siempre disponible).
        String porZona;
        if (tocada) {
            porZona = "Elegiste «" + etiqueta + "» en el maniquí.";
        } else if (!motivos.isEmpty()) {
            porZona = "Marcamos «" + etiqueta + "» porque mencionaste: " + String.join(", ", motivos) + ".";
        } else {
            porZona = "Marcamos «" + etiqueta + "» según la especialidad que mejor encaja con lo que describes.";
        }
        String porEspecialidad;
        String detalle = ZonaCorporal.descripcion(especialidad);
        if (especialidad == null) {
            porEspecialidad = "Todavía no hay médicos registrados de la especialidad que corresponde a esta zona. "
                    + "Puedes consultar con Medicina General para que te derive.";
        } else {
            porEspecialidad = "Te orientamos a " + especialidad + (detalle == null ? "" : ", que atiende " + detalle)
                    + (motivos.isEmpty() || tocada ? "" : ", y mencionaste: " + String.join(", ", motivos)) + ".";
        }
        String resumen = especialidad == null
                ? "Con lo que indicas, la zona de interés es «" + etiqueta + "». Aún no hay una especialidad disponible para ella."
                : "Con lo que indicas, la zona de interés es «" + etiqueta + "» y la especialidad sugerida es "
                        + especialidad + ". Es una orientación para buscar médico, no un diagnóstico.";
        List<String> causas = List.of();
        List<String> cuidados = CUIDADOS;
        List<String> alarmas = ALARMAS;
        boolean ia = false;

        // 3) La IA (si está disponible) amplía la explicación; si falla, se queda lo anterior.
        JsonNode ai = preguntarIa(sexoTexto, texto, etiqueta, especialidad, tocada);
        if (ai != null) {
            String r = texto(ai.path("summary"), 700);
            if (r != null) {
                resumen = r;
                ia = true;
                String pe = texto(ai.path("why_specialty"), 400);
                if (pe != null) porEspecialidad = pe;
                causas = lista(ai.path("probable_causes"), 4, 220);
                List<String> c = lista(ai.path("self_care"), 3, 220);
                if (!c.isEmpty()) cuidados = c;
                List<String> a = lista(ai.path("warning_signs"), 4, 220);
                if (!a.isEmpty()) alarmas = a;
            }
        }

        return new OrientacionCorporalResponse(sexoTexto, sexoEtiqueta, zona, etiqueta, especialidad, urgente, ia,
                resumen, porZona, porEspecialidad, causas, cuidados, alarmas, alternativas, AVISO,
                LocalDateTime.now(LIMA).format(FECHA));
    }

    // ------------------------------------------------------------------ IA

    /** Un solo llamado que devuelve la explicación en JSON, o null si la IA no está disponible. */
    private JsonNode preguntarIa(String sexo, String sintomas, String zona, String especialidad, boolean tocada) {
        if (!StringUtils.hasText(apiKey)) return null;
        try {
            String instrucciones = systemPrompt + " Reglas obligatorias: responde en español claro y cercano. Eres un "
                    + "orientador para buscar médico, no un profesional clínico: NO des un diagnóstico definitivo, NO "
                    + "recetes ni menciones fármacos ni dosis, y usa lenguaje de posibilidad (\"podría\", \"suele\"). Si lo "
                    + "descrito sugiere gravedad, dilo con claridad. Devuelve únicamente JSON: {\"summary\": string "
                    + "(2 a 4 oraciones: qué zona del cuerpo está implicada, qué podría estar pasando en términos generales "
                    + "y por qué conviene la especialidad sugerida), \"why_specialty\": string (1 a 2 oraciones), "
                    + "\"probable_causes\": [hasta 4 strings: posibles causas que el especialista deberá descartar, "
                    + "redactadas como hipótesis], \"self_care\": [hasta 3 strings: cuidados seguros mientras llega la "
                    + "cita, sin medicamentos], \"warning_signs\": [hasta 4 strings: señales de alarma para acudir a "
                    + "emergencias]}.";
            String mensaje = "Paciente: " + sexo + ". Síntomas descritos: "
                    + (sintomas.isBlank() ? "(no los describió; solo tocó la zona en el maniquí)" : sintomas)
                    + ". Zona del cuerpo " + (tocada ? "elegida por el paciente" : "deducida de los síntomas") + ": " + zona
                    + ". Especialidad sugerida: " + (especialidad == null ? "ninguna disponible" : especialidad) + ".";
            return callModel(instrucciones, mensaje);
        } catch (Exception ex) {
            log.warn("Orientación corporal: IA no disponible ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
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

    /** Texto de la IA recortado; null si no es texto o está vacío. */
    private String texto(JsonNode node, int max) {
        if (node == null || !node.isTextual() || !StringUtils.hasText(node.asText())) return null;
        String t = node.asText().trim();
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    /** Lista de textos de la IA: sin vacíos, recortados y con tope de elementos. */
    private List<String> lista(JsonNode node, int maxItems, int maxLen) {
        List<String> out = new ArrayList<>();
        if (node == null || !node.isArray()) return out;
        for (JsonNode item : node) {
            String t = texto(item, maxLen);
            if (t != null) out.add(t);
            if (out.size() >= maxItems) break;
        }
        return out;
    }

    // ------------------------------------------------------------------ especialidades

    /** Especialidades con médicos activos (las mismas que usa el chat), de la A a la Z. */
    private List<String> especialidadesActivas() {
        return medicos.findByUsuarioEstadoOrderByRatingPromedioDesc(EstadoUsuario.ACTIVO).stream()
                .map(m -> m.getEspecialidad().getNombre())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private String coincidir(String valor, List<String> especialidades) {
        if (!StringUtils.hasText(valor)) return null;
        String buscado = AsistenteTexto.normalizar(valor);
        return especialidades.stream().filter(s -> AsistenteTexto.normalizar(s).equals(buscado)).findFirst().orElse(null);
    }
}
