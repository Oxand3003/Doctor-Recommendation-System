package utp.edu.pe.recomendaciones.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import utp.edu.pe.recomendaciones.dto.DoctorProfileView;
import utp.edu.pe.recomendaciones.dto.DoctorProfileView.ConsultorioView;
import utp.edu.pe.recomendaciones.dto.DoctorProfileView.CriterioView;
import utp.edu.pe.recomendaciones.dto.DoctorProfileView.DayView;
import utp.edu.pe.recomendaciones.dto.DoctorProfileView.FormacionView;
import utp.edu.pe.recomendaciones.dto.DoctorProfileView.ReviewView;
import utp.edu.pe.recomendaciones.dto.DoctorProfileView.ServicioView;
import utp.edu.pe.recomendaciones.dto.SlotView;
import utp.edu.pe.recomendaciones.model.Cita;
import utp.edu.pe.recomendaciones.model.Establecimiento;
import utp.edu.pe.recomendaciones.model.HorarioAtencion;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.Valoracion;
import utp.edu.pe.recomendaciones.model.enums.EstadoCita;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.model.enums.Modalidad;
import utp.edu.pe.recomendaciones.model.FotoMedico;
import utp.edu.pe.recomendaciones.model.enums.ModalidadAtencion;
import utp.edu.pe.recomendaciones.model.enums.TipoFoto;
import utp.edu.pe.recomendaciones.repository.CitaRepository;
import utp.edu.pe.recomendaciones.repository.EstablecimientoRepository;
import utp.edu.pe.recomendaciones.repository.FormacionMedicoRepository;
import utp.edu.pe.recomendaciones.repository.FotoMedicoRepository;
import utp.edu.pe.recomendaciones.repository.HorarioAtencionRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.repository.ServicioMedicoRepository;
import utp.edu.pe.recomendaciones.repository.ValoracionRepository;

@Service
public class DoctorProfileService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final String[] DIAS = {"LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM"};
    private static final String[] DIAS_CORTO = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
    private static final String[] MESES = {"Ene", "Feb", "Mar", "Abr", "May", "Jun",
            "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"};
    private static final DateTimeFormatter FECHA_RESENA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"));
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int DIAS_A_MOSTRAR = 5;

    private final MedicoRepository medicoRepository;
    private final HorarioAtencionRepository horarioRepository;
    private final ServicioMedicoRepository servicioRepository;
    private final FormacionMedicoRepository formacionRepository;
    private final ValoracionRepository valoracionRepository;
    private final CitaRepository citaRepository;
    private final FotoMedicoRepository fotoRepository;
    private final EstablecimientoRepository establecimientoRepository;

    public DoctorProfileService(MedicoRepository medicoRepository,
                                HorarioAtencionRepository horarioRepository,
                                ServicioMedicoRepository servicioRepository,
                                FormacionMedicoRepository formacionRepository,
                                ValoracionRepository valoracionRepository,
                                CitaRepository citaRepository,
                                FotoMedicoRepository fotoRepository,
                                EstablecimientoRepository establecimientoRepository) {
        this.medicoRepository = medicoRepository;
        this.horarioRepository = horarioRepository;
        this.servicioRepository = servicioRepository;
        this.formacionRepository = formacionRepository;
        this.valoracionRepository = valoracionRepository;
        this.citaRepository = citaRepository;
        this.fotoRepository = fotoRepository;
        this.establecimientoRepository = establecimientoRepository;
    }

    // ------------------------------------------------------------------
    // PERFIL
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public DoctorProfileView getProfile(Long id) {
        Medico m = buscarActivo(id);
        Usuario u = m.getUsuario();
        LocalDate hoy = LocalDate.now(LIMA);

        List<HorarioAtencion> horarios = horarioRepository.findByMedicoIdAndActivoTrue(id);
        // El desglose por criterio usa TODAS las valoraciones (igual que ratingPromedio); la lista de
        // reseñas usa solo las que el médico dejó visibles. Ocultar nunca modifica la reseña.
        List<Valoracion> valoraciones = valoracionRepository.findByMedicoIdOrderByCreadaEnDesc(id);
        List<Valoracion> visibles = valoracionRepository.findByMedicoIdAndVisiblePublicoTrueOrderByCreadaEnDesc(id);

        boolean presencial = horarios.stream().anyMatch(h ->
                h.getModalidad() == ModalidadAtencion.PRESENCIAL || h.getModalidad() == ModalidadAtencion.AMBAS);
        boolean tele = horarios.stream().anyMatch(h ->
                h.getModalidad() == ModalidadAtencion.TELECONSULTA || h.getModalidad() == ModalidadAtencion.AMBAS);

        String modalidadesTexto = presencial && tele ? "Teleconsulta y presencial"
                : tele ? "Solo teleconsulta" : "Solo presencial";

        String nombreCompleto = nombrePublico(u);

        String subtitulo = m.getEspecialidad().getNombre()
                + (m.getTituloProfesional() != null && !m.getTituloProfesional().isBlank()
                        ? " — " + m.getTituloProfesional() : "");

        String biografia = m.getBiografia() != null && !m.getBiografia().isBlank()
                ? m.getBiografia()
                : "Este especialista aún no ha agregado su biografía.";

        double score = (m.getRatingPromedio() / 5.0) * 0.7
                + (Math.min(m.getAniosExperiencia(), 20) / 20.0) * 0.3;

        // Precios: costoConsulta = presencial/base; teleconsulta hereda el base si no tiene precio propio.
        BigDecimal pPresencial = m.getCostoConsulta();
        BigDecimal pTele = m.precioPara(Modalidad.TELECONSULTA);
        String precioPrincipal = precio(pPresencial != null ? pPresencial : pTele);

        boolean disponibleHoy = generarTurnos(horarios, id, hoy, null).stream().anyMatch(SlotView::disponible);

        return new DoctorProfileView(
                m.getId(),
                nombreCompleto,
                iniciales(u),
                m.getEspecialidad().getNombre(),
                subtitulo,
                m.getCmp(),
                m.getRne() == null || m.getRne().isBlank() ? "—" : m.getRne(),
                m.getEstablecimiento() != null ? m.getEstablecimiento().getNombre() : "Por confirmar",
                biografia,
                precioPrincipal,
                m.getTotalValoraciones() == 0 ? "Nuevo" : String.format(Locale.US, "%.1f", m.getRatingPromedio()),
                "★".repeat((int) Math.round(m.getRatingPromedio())),
                m.getTotalValoraciones(),
                m.getAniosExperiencia(),
                citaRepository.countByMedicoIdAndEstado(id, EstadoCita.COMPLETADA),
                (int) Math.round(score * 100),
                disponibleHoy,
                presencial,
                tele,
                presencial ? "PRESENCIAL" : "TELECONSULTA",
                modalidadesTexto,
                formacionRepository.findByMedicoId(id).stream()
                        .map(f -> new FormacionView(f.getTitulo(),
                                f.getInstitucion() + (f.getAnio() != null ? " (" + f.getAnio() + ")" : "")))
                        .toList(),
                servicioRepository.findByMedicoIdAndActivoTrue(id).stream()
                        .map(s -> new ServicioView(s.getNombre(), s.getDescripcion(), precio(s.getPrecio())))
                        .toList(),
                consultorio(m.getEstablecimiento()),
                resumenHorarios(horarios),
                desglose(valoraciones),
                visibles.stream().limit(10).map(this::toReview).toList(),
                dias(horarios, hoy),
                m.getUniversidad() == null || m.getUniversidad().isBlank() ? null
                        : m.getUniversidad() + (m.getAnioEgreso() != null ? " (" + m.getAnioEgreso() + ")" : ""),
                fotoPerfilId(id),
                fotoRepository.findByMedicoIdAndTipoOrderByCreadoEnDesc(id, TipoFoto.GALERIA).stream()
                        .map(FotoMedico::getId).toList(),
                precio(pPresencial),
                precio(pTele),
                m.getCostoPorHora() == null ? null : precio(m.getCostoPorHora()),
                duracion(horarios),
                otrasSedes(m));
    }

    /** Sedes propias del médico distintas de la principal (que ya sale como "consultorio"). */
    private List<ConsultorioView> otrasSedes(Medico m) {
        Long principal = m.getEstablecimiento() != null ? m.getEstablecimiento().getId() : null;
        return establecimientoRepository.findByPropietarioIdOrderByNombreAsc(m.getId()).stream()
                .filter(e -> !e.getId().equals(principal))
                .map(this::consultorio)
                .toList();
    }

    /** "40 min" o "30–40 min" según las franjas activas; null si no hay horarios. */
    private String duracion(List<HorarioAtencion> horarios) {
        var stats = horarios.stream().mapToInt(HorarioAtencion::getDuracionCitaMin).filter(d -> d > 0).summaryStatistics();
        if (stats.getCount() == 0) return null;
        return stats.getMin() == stats.getMax() ? stats.getMin() + " min" : stats.getMin() + "–" + stats.getMax() + " min";
    }

    private Long fotoPerfilId(Long medicoId) {
        return fotoRepository.findFirstByMedicoIdAndTipoOrderByCreadoEnDesc(medicoId, TipoFoto.PERFIL)
                .map(FotoMedico::getId).orElse(null);
    }

    // ------------------------------------------------------------------
    // TURNOS (se consulta por AJAX desde el JS)
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<SlotView> getSlots(Long medicoId, LocalDate fecha, Modalidad modalidad) {
        buscarActivo(medicoId);
        if (fecha.isBefore(LocalDate.now(LIMA))) {
            return List.of();
        }
        List<HorarioAtencion> horarios = horarioRepository.findByMedicoIdAndActivoTrue(medicoId);
        return generarTurnos(horarios, medicoId, fecha, modalidad);
    }

    /** modalidad = null → acepta cualquiera. */
    private List<SlotView> generarTurnos(List<HorarioAtencion> horarios, Long medicoId,
                                         LocalDate fecha, Modalidad modalidad) {
        int dia = fecha.getDayOfWeek().getValue();

        List<HorarioAtencion> delDia = horarios.stream()
                .filter(h -> h.getDiaSemana() == dia)
                .filter(h -> modalidad == null
                        || h.getModalidad() == ModalidadAtencion.AMBAS
                        || h.getModalidad().name().equals(modalidad.name()))
                .sorted(Comparator.comparing(HorarioAtencion::getHoraInicio))
                .toList();

        Set<LocalTime> ocupados = citaRepository
                .findByMedicoIdAndFechaHoraInicioBetween(medicoId, fecha.atStartOfDay(), fecha.atTime(LocalTime.MAX))
                .stream()
                .filter(c -> c.getEstado() != EstadoCita.CANCELADA)
                .map(c -> c.getFechaHoraInicio().toLocalTime())
                .collect(Collectors.toSet());

        LocalDateTime ahora = LocalDateTime.now(LIMA);
        List<SlotView> turnos = new ArrayList<>();
        Set<LocalTime> vistos = new HashSet<>();

        for (HorarioAtencion h : delDia) {
            int dur = h.getDuracionCitaMin();
            if (dur <= 0) continue;
            LocalTime t = h.getHoraInicio();
            while (!t.plusMinutes(dur).isAfter(h.getHoraFin())) {
                if (vistos.add(t)) {
                    boolean pasado = fecha.equals(ahora.toLocalDate()) && !t.isAfter(ahora.toLocalTime());
                    turnos.add(new SlotView(t.format(HORA), !ocupados.contains(t) && !pasado));
                }
                LocalTime siguiente = t.plusMinutes(dur);
                if (siguiente.isBefore(t)) break; // cruzó medianoche
                t = siguiente;
            }
        }
        turnos.sort(Comparator.comparing(SlotView::hora));
        return turnos;
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------
    private Medico buscarActivo(Long id) {
        return medicoRepository.findByIdAndUsuarioEstado(id, EstadoUsuario.ACTIVO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Médico no encontrado"));
    }

    private List<DayView> dias(List<HorarioAtencion> horarios, LocalDate hoy) {
        Set<Integer> diasConHorario = horarios.stream().map(HorarioAtencion::getDiaSemana)
                .collect(Collectors.toSet());
        List<DayView> lista = new ArrayList<>();
        boolean yaActivo = false;
        for (int i = 0; i < DIAS_A_MOSTRAR; i++) {
            LocalDate f = hoy.plusDays(i);
            int idx = f.getDayOfWeek().getValue() - 1;
            boolean disponible = diasConHorario.contains(idx + 1);
            boolean activo = disponible && !yaActivo;
            if (activo) yaActivo = true;
            String sub = i == 0 ? "Hoy" : i == 1 ? "Mañana" : disponible ? "Disp." : "—";
            String etiqueta = DIAS_CORTO[idx] + " " + f.getDayOfMonth() + " " + MESES[f.getMonthValue() - 1];
            lista.add(new DayView(f.toString(), DIAS[idx], f.getDayOfMonth(), sub, etiqueta, disponible, activo));
        }
        return lista;
    }

    private List<String> resumenHorarios(List<HorarioAtencion> horarios) {
        Map<Integer, List<String>> porDia = new TreeMap<>();
        horarios.stream()
                .sorted(Comparator.comparing(HorarioAtencion::getHoraInicio))
                .forEach(h -> porDia.computeIfAbsent(h.getDiaSemana(), k -> new ArrayList<>())
                        .add(h.getHoraInicio().format(HORA) + " – " + h.getHoraFin().format(HORA)
                                + sufijoModalidad(h.getModalidad())));
        return porDia.entrySet().stream()
                .map(e -> DIAS_CORTO[e.getKey() - 1] + ": " + String.join(" · ", e.getValue()))
                .toList();
    }

    private String sufijoModalidad(ModalidadAtencion m) {
        return switch (m) {
            case PRESENCIAL -> " (presencial)";
            case TELECONSULTA -> " (teleconsulta)";
            case AMBAS -> "";
        };
    }

    /** Package-private y static: también lo usa ResenaService para el panel privado. */
    static List<CriterioView> desglose(List<Valoracion> vs) {
        List<CriterioView> out = new ArrayList<>();
        agregarCriterio(out, "Calidez y atención", vs, Valoracion::getCalidez);
        agregarCriterio(out, "Explicación del diagnóstico", vs, Valoracion::getExplicacion);
        agregarCriterio(out, "Puntualidad", vs, Valoracion::getPuntualidad);
        agregarCriterio(out, "Instalaciones", vs, Valoracion::getInstalaciones);
        return out;
    }

    private static void agregarCriterio(List<CriterioView> out, String nombre, List<Valoracion> vs,
                                 Function<Valoracion, Integer> fn) {
        vs.stream().map(fn).filter(Objects::nonNull).mapToInt(Integer::intValue).average()
                .ifPresent(avg -> out.add(new CriterioView(nombre,
                        String.format(Locale.US, "%.1f", avg), (int) Math.round(avg / 5.0 * 100))));
    }

    private ReviewView toReview(Valoracion v) {
        Usuario p = v.getPaciente();
        Cita c = v.getCita();
        String autor = primerToken(p.getNombres()) + " " + primerToken(p.getApellidos()).charAt(0) + ".";
        String modalidad = c.getModalidad() == Modalidad.TELECONSULTA ? "Teleconsulta" : "Consulta presencial";
        return new ReviewView(iniciales(p), autor, c.getFechaHoraInicio().format(FECHA_RESENA), modalidad,
                "★".repeat(v.getPuntajeGeneral()), v.getComentario());
    }

    private ConsultorioView consultorio(Establecimiento e) {
        if (e == null) return null;
        String distrito = e.getDistrito().getNombre() + ", " + e.getDistrito().getProvincia();
        return new ConsultorioView(e.getNombre(), e.getDireccion(), e.getReferencia(), distrito);
    }

    private String precio(BigDecimal p) {
        return p == null ? "—" : p.stripTrailingZeros().toPlainString();
    }

    /** "Dra. Rosa Medina": usado también por el perfil privado para mostrar el mismo nombre. */
    static String nombrePublico(Usuario u) {
        return tratamiento(u.getNombres()) + " " + primerToken(u.getNombres())
                + " " + primerToken(u.getApellidos());
    }

    static String iniciales(Usuario u) {
        return ("" + primerToken(u.getNombres()).charAt(0) + primerToken(u.getApellidos()).charAt(0)).toUpperCase();
    }

    static String primerToken(String s) {
        return s == null || s.isBlank() ? "?" : s.trim().split("\\s+")[0];
    }

    /** El modelo no tiene género: se deduce del primer nombre. Ideal: agregar un campo `sexo` a Medico. */
    static String tratamiento(String nombres) {
        return primerToken(nombres).toLowerCase().endsWith("a") ? "Dra." : "Dr.";
    }
}