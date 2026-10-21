package utp.edu.pe.recomendaciones.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.dto.AgendaView;
import utp.edu.pe.recomendaciones.dto.AgendaView.Dia;
import utp.edu.pe.recomendaciones.dto.CitaCancelada;
import utp.edu.pe.recomendaciones.dto.CitaDetalleView;
import utp.edu.pe.recomendaciones.dto.CitaResumen;
import utp.edu.pe.recomendaciones.dto.DashboardMedicoView;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Cita;
import utp.edu.pe.recomendaciones.model.Establecimiento;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.EstadoCita;
import utp.edu.pe.recomendaciones.model.enums.Modalidad;
import utp.edu.pe.recomendaciones.repository.AgendaCitaRepository;

/**
 * Dashboard y agenda del médico. Lee citas y, con eliminar(), permite cancelar una reserva propia: la reserva
 * sigue siendo la misma. El médico sale siempre de la sesión (MedicoSesion) y toda consulta filtra por su id;
 * una cita ajena se trata como inexistente.
 *
 * Definiciones (todas sobre citas reales):
 *  - pacientes atendidos: pacientes distintos con alguna cita COMPLETADA.
 *  - citas programadas: CONFIRMADA con inicio desde ahora.
 *  - citas pendientes: PENDIENTE (por confirmar) con inicio desde ahora.
 *  - próximas citas: las 5 siguientes PENDIENTE o CONFIRMADA desde ahora.
 * Cita no guarda la sede: para consultas presenciales se muestra la sede principal actual del médico.
 */
@Service
public class AgendaMedicoService {

    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FECHA_LARGA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("EEE d MMM", ES);
    private static final DateTimeFormatter DIA_CORTO = DateTimeFormatter.ofPattern("d 'de' MMM", ES);
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MOTIVO_MAX = 300;
    private static final DateTimeFormatter RESERVA = DateTimeFormatter.ofPattern("d/MM/yyyy HH:mm");

    private final MedicoSesion sesion;
    private final AgendaCitaRepository citas;

    public AgendaMedicoService(MedicoSesion sesion, AgendaCitaRepository citas) {
        this.sesion = sesion;
        this.citas = citas;
    }

    // ================== DASHBOARD ==================
    @Transactional(readOnly = true)
    public DashboardMedicoView dashboard(String email) {
        Medico m = sesion.actual(email);
        LocalDateTime ahora = LocalDateTime.now();
        List<CitaResumen> proximas = citas.proximas(m.getId(),
                        EnumSet.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA), ahora, PageRequest.of(0, 5))
                .stream().map(AgendaMedicoService::resumen).toList();
        return new DashboardMedicoView(
                capitalizar(m.getUsuario().getNombres()),
                citas.contarPacientesDistintos(m.getId(), EstadoCita.COMPLETADA),
                citas.contarDesde(m.getId(), EstadoCita.CONFIRMADA, ahora),
                citas.contarDesde(m.getId(), EstadoCita.PENDIENTE, ahora),
                m.getTotalValoraciones() == 0 ? "Nuevo" : String.format(Locale.US, "%.1f", m.getRatingPromedio()),
                "★".repeat((int) Math.round(m.getRatingPromedio())),
                m.getTotalValoraciones(), proximas);
    }

    // ================== AGENDA ==================
    /** vista: "dia", "semana" o "mes" (otro valor → semana). fecha: cualquier día dentro del periodo. */
    @Transactional(readOnly = true)
    public AgendaView agenda(String email, String vista, LocalDate fecha) {
        Medico m = sesion.actual(email);
        LocalDate hoy = LocalDate.now();
        LocalDate f = fecha == null ? hoy : fecha;
        String v = "dia".equals(vista) || "mes".equals(vista) ? vista : "semana";

        LocalDate desde, hasta, anterior, siguiente;
        String titulo;
        switch (v) {
            case "dia" -> {
                desde = f;
                hasta = f;
                anterior = f.minusDays(1);
                siguiente = f.plusDays(1);
                titulo = capitalizar(f.format(FECHA_LARGA));
            }
            case "mes" -> {
                LocalDate primero = f.withDayOfMonth(1);
                desde = primero.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                hasta = primero.with(TemporalAdjusters.lastDayOfMonth()).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                anterior = primero.minusMonths(1);
                siguiente = primero.plusMonths(1);
                titulo = capitalizar(f.format(MES));
            }
            default -> {
                desde = f.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                hasta = desde.plusDays(6);
                anterior = f.minusWeeks(1);
                siguiente = f.plusWeeks(1);
                titulo = desde.format(DIA_CORTO) + " – " + hasta.format(DIA_CORTO) + " de " + hasta.getYear();
            }
        }

        Map<LocalDate, List<CitaResumen>> porDia = new TreeMap<>();
        int total = 0;
        for (Cita c : citas.entre(m.getId(), desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay())) {
            porDia.computeIfAbsent(c.getFechaHoraInicio().toLocalDate(), k -> new ArrayList<>()).add(resumen(c));
            if (v.equals("mes") ? c.getFechaHoraInicio().getMonth() == f.getMonth() : true) total++;
        }

        List<Dia> dias = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            dias.add(new Dia(d, capitalizar(d.format(FECHA_CORTA)), d.getDayOfMonth(), d.equals(hoy),
                    !v.equals("mes") || d.getMonth() == f.getMonth(), porDia.getOrDefault(d, List.of())));
        }
        return new AgendaView(v, titulo, f, anterior, siguiente, hoy, total, dias);
    }

    // ================== DETALLE ==================
    @Transactional(readOnly = true)
    public CitaDetalleView detalle(String email, Long id) {
        Medico m = sesion.actual(email);
        Cita c = citas.findByIdAndMedicoId(id, m.getId())
                .orElseThrow(() -> new NegocioException("La cita indicada no existe."));
        Usuario p = c.getPaciente();
        boolean presencial = c.getModalidad() == Modalidad.PRESENCIAL;
        Establecimiento sede = presencial ? m.getEstablecimiento() : null;
        String distrito = sede != null && sede.getDistrito() != null ? ", " + sede.getDistrito().getNombre() : "";
        BigDecimal precio = c.getPrecio();
        return new CitaDetalleView(c.getId(),
                (p.getNombres() + " " + p.getApellidos()).trim(), vacioAGuion(p.getTelefono()), p.getEmail(),
                capitalizar(c.getFechaHoraInicio().format(FECHA_LARGA)), c.getFechaHoraInicio().toLocalDate(),
                c.getFechaHoraInicio().format(HORA), c.getFechaHoraFin().format(HORA),
                (int) Duration.between(c.getFechaHoraInicio(), c.getFechaHoraFin()).toMinutes(),
                etiquetaModalidad(c.getModalidad()), presencial,
                presencial ? (sede != null ? sede.getNombre() : "Por confirmar") : "Atención virtual",
                sede != null ? sede.getDireccion() + distrito : null,
                sede != null && sede.getReferencia() != null && !sede.getReferencia().isBlank() ? sede.getReferencia() : null,
                etiquetaEstado(c.getEstado()), claveEstado(c.getEstado()),
                c.getMotivo() == null || c.getMotivo().isBlank() ? null : c.getMotivo(),
                c.getEstado() == EstadoCita.CANCELADA && c.getMotivoCancelacion() != null
                        && !c.getMotivoCancelacion().isBlank() ? c.getMotivoCancelacion() : null,
                precio == null ? "—" : "S/ " + precio.setScale(2, java.math.RoundingMode.HALF_UP),
                c.getCreadoEn() == null ? "—" : c.getCreadoEn().format(RESERVA),
                enlaceWhatsapp(p.getTelefono(), p.getNombres(), m, c),
                esVigente(c.getEstado()));
    }

    // ================== ELIMINAR (cancelar) ==================
    /**
     * El médico elimina una reserva suya. La cita pasa a CANCELADA (no se borra la fila): el turno queda libre
     * para otros pacientes y el paciente la sigue viendo en su historial como «Cancelada». Solo se pueden
     * eliminar citas PENDIENTE o CONFIRMADA. Devuelve lo necesario para avisar al paciente por correo.
     */
    @Transactional
    public CitaCancelada eliminar(String email, Long id, String motivoRaw) {
        Medico m = sesion.actual(email);
        Cita c = citas.findByIdAndMedicoId(id, m.getId())
                .orElseThrow(() -> new NegocioException("La cita indicada no existe."));
        if (!esVigente(c.getEstado())) {
            throw new NegocioException("Solo se pueden eliminar citas pendientes o confirmadas.");
        }
        String motivo = motivoRaw == null ? "" : motivoRaw.trim();
        if (motivo.length() > MOTIVO_MAX) {
            throw new NegocioException("El motivo no puede superar " + MOTIVO_MAX + " caracteres.");
        }
        c.setEstado(EstadoCita.CANCELADA);
        c.setMotivoCancelacion(motivo.isEmpty() ? "Cancelada por el médico" : motivo);
        citas.saveAndFlush(c);

        Usuario p = c.getPaciente();
        return new CitaCancelada(c.getId(), CitaPacienteService.codigo(c.getId()),
                (p.getNombres() + " " + p.getApellidos()).trim(), p.getEmail(),
                DoctorProfileService.nombrePublico(m.getUsuario()),
                capitalizar(c.getFechaHoraInicio().format(FECHA_LARGA)),
                c.getFechaHoraInicio().format(HORA) + " – " + c.getFechaHoraFin().format(HORA),
                etiquetaModalidad(c.getModalidad()), motivo.isEmpty() ? null : motivo,
                c.getFechaHoraInicio().toLocalDate());
    }

    private static boolean esVigente(EstadoCita e) {
        return e == EstadoCita.PENDIENTE || e == EstadoCita.CONFIRMADA;
    }

    // ================== WhatsApp ==================
    /**
     * Enlace wa.me al teléfono que el paciente registró, con un mensaje listo para coordinar el pago.
     * null si el número no sirve para WhatsApp.
     */
    static String enlaceWhatsapp(String telefono, String nombrePaciente, Medico m, Cita c) {
        String numero = numeroWhatsapp(telefono);
        if (numero == null) return null;
        String mensaje = "Hola " + primerToken(nombrePaciente) + ", soy " + DoctorProfileService.nombrePublico(m.getUsuario())
                + " de MediCerca. Te escribo por tu cita " + CitaPacienteService.codigo(c.getId()) + " del "
                + c.getFechaHoraInicio().format(FECHA_CORTA) + " a las " + c.getFechaHoraInicio().format(HORA)
                + " (" + etiquetaModalidad(c.getModalidad()).toLowerCase(Locale.ROOT)
                + ") para coordinar el pago. ¿Te parece bien?";
        return "https://wa.me/" + numero + "?text=" + java.net.URLEncoder.encode(mensaje, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    /** Solo dígitos con código de país; un celular peruano de 9 dígitos recibe el 51. null si no es utilizable. */
    static String numeroWhatsapp(String telefono) {
        if (telefono == null) return null;
        String t = telefono.trim();
        boolean conMas = t.startsWith("+");
        String d = t.replaceAll("\\D", "");
        if (d.startsWith("00")) { d = d.substring(2); conMas = true; }
        if (d.isEmpty()) return null;
        if (!conMas && d.length() == 9 && d.startsWith("9")) d = "51" + d;
        return d.length() >= 10 && d.length() <= 15 ? d : null;
    }

    private static String primerToken(String s) {
        if (s == null || s.isBlank()) return "";
        return s.trim().split("\\s+")[0];
    }

    // ================== Auxiliares ==================
    private static CitaResumen resumen(Cita c) {
        Usuario p = c.getPaciente();
        return new CitaResumen(c.getId(), (p.getNombres() + " " + p.getApellidos()).trim(),
                c.getFechaHoraInicio().format(FECHA_CORTA), c.getFechaHoraInicio().format(HORA),
                c.getFechaHoraFin().format(HORA), etiquetaModalidad(c.getModalidad()),
                etiquetaEstado(c.getEstado()), claveEstado(c.getEstado()));
    }

    private static String etiquetaModalidad(Modalidad m) {
        return m == Modalidad.TELECONSULTA ? "Teleconsulta" : "Presencial";
    }

    private static String etiquetaEstado(EstadoCita e) {
        return switch (e) {
            case PENDIENTE -> "Pendiente";
            case CONFIRMADA -> "Confirmada";
            case CANCELADA -> "Cancelada";
            case COMPLETADA -> "Completada";
            case NO_ASISTIO -> "No asistió";
        };
    }

    private static String claveEstado(EstadoCita e) {
        return e.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String vacioAGuion(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static String capitalizar(String s) {
        return s == null || s.isBlank() ? "" : s.substring(0, 1).toUpperCase(ES) + s.substring(1);
    }
}
