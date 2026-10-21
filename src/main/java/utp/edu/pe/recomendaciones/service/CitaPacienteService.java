package utp.edu.pe.recomendaciones.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.dto.PerfilReserva;
import utp.edu.pe.recomendaciones.dto.ReservaCreada;
import utp.edu.pe.recomendaciones.exception.ConflictoReservaException;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Cita;
import utp.edu.pe.recomendaciones.model.HorarioAtencion;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.EstadoCita;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.model.enums.Modalidad;
import utp.edu.pe.recomendaciones.model.enums.ModalidadAtencion;
import utp.edu.pe.recomendaciones.repository.CitaRepository;
import utp.edu.pe.recomendaciones.repository.HorarioAtencionRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

/**
 * Reserva de citas del paciente.
 *
 * Qué garantiza una reserva:
 *  1. El paciente sale SIEMPRE de la sesión (correo), nunca de un parámetro del navegador.
 *  2. El turno pedido existe de verdad: cae en una franja ACTIVA del médico para ese día y modalidad, alineado a
 *     la duración de la franja y todavía en el futuro (misma regla con la que DoctorProfileService genera los
 *     turnos que ve el paciente).
 *  3. No hay citas duplicadas: se bloquea la fila del médico mientras se reserva (dos pacientes simultáneos se
 *     atienden uno tras otro), se rechaza cualquier cita no cancelada del médico que se cruce con el turno, y
 *     se rechaza que el mismo paciente tenga otra cita a esa hora. La restricción UNIQUE (medico, inicio) de la
 *     tabla queda como última barrera.
 *  4. La cita nace PENDIENTE. Como los turnos libres, la agenda y el dashboard del médico se calculan con la
 *     misma tabla de citas, el turno queda ocupado y visible para ambos perfiles al instante.
 *  5. Una cita CANCELADA libera su turno; si otro paciente lo reserva se reutiliza esa fila (la restricción
 *     UNIQUE no permite dos filas con el mismo médico e inicio).
 */
@Service
public class CitaPacienteService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", ES);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MOTIVO_MAX = 500;

    private final UsuarioRepository usuarios;
    private final MedicoRepository medicos;
    private final HorarioAtencionRepository horarios;
    private final CitaRepository citas;
    private final MensajeriaService mensajeria;

    public CitaPacienteService(UsuarioRepository usuarios, MedicoRepository medicos,
                               HorarioAtencionRepository horarios, CitaRepository citas,
                               MensajeriaService mensajeria) {
        this.usuarios = usuarios;
        this.medicos = medicos;
        this.horarios = horarios;
        this.citas = citas;
        this.mensajeria = mensajeria;
    }

    // ------------------------------------------------------------------
    // Datos para la ventana de reserva del perfil público
    // ------------------------------------------------------------------
    /** email = null (o desconocido) → visitante anónimo. */
    @Transactional(readOnly = true)
    public PerfilReserva perfilReserva(String email) {
        if (email == null || email.isBlank()) return PerfilReserva.ANONIMO;
        return usuarios.findByEmail(email.trim().toLowerCase())
                .map(u -> {
                    boolean paciente = "PACIENTE".equals(u.getRol().getNombre()) && u.getEstado() == EstadoUsuario.ACTIVO;
                    if (!paciente) return new PerfilReserva("OTRO", "", "", "");
                    return new PerfilReserva("PACIENTE", (u.getNombres() + " " + u.getApellidos()).trim(),
                            u.getDni(), u.getTelefono());
                })
                .orElse(PerfilReserva.ANONIMO);
    }

    // ------------------------------------------------------------------
    // Reservar
    // ------------------------------------------------------------------
    /**
     * Crea la cita PENDIENTE. Lanza NegocioException (datos inválidos) o ConflictoReservaException (turno
     * tomado). Con saveAndFlush el UNIQUE de la base salta aquí mismo, dentro de la transacción.
     */
    @Transactional
    public ReservaCreada reservar(String email, String medicoIdRaw, String fechaRaw, String horaRaw,
                                  String modalidadRaw, String motivoRaw) {
        Usuario paciente = pacienteActivo(email);

        Long medicoId = entero(medicoIdRaw);
        LocalDate fecha = fecha(fechaRaw);
        LocalTime hora = hora(horaRaw);
        Modalidad modalidad = modalidad(modalidadRaw);
        String motivo = motivo(motivoRaw);

        LocalDateTime inicio = LocalDateTime.of(fecha, hora);
        if (!inicio.isAfter(LocalDateTime.now(LIMA))) {
            throw new NegocioException("Ese horario ya pasó. Elige un turno posterior.");
        }

        // Bloquea la fila del médico: desde aquí las reservas del mismo médico se hacen una a una.
        Medico medico = medicos.bloquearPorId(medicoId)
                .orElseThrow(() -> new NegocioException("El médico indicado no existe."));
        if (medico.getUsuario().getEstado() != EstadoUsuario.ACTIVO) {
            throw new NegocioException("Este médico no está recibiendo reservas por ahora.");
        }

        HorarioAtencion franja = franjaDelTurno(medico.getId(), fecha, hora, modalidad);
        LocalDateTime fin = inicio.plusMinutes(franja.getDuracionCitaMin());

        if (citas.contarCruzadasDelMedico(medico.getId(), inicio, fin, EstadoCita.CANCELADA) > 0) {
            throw new ConflictoReservaException(
                    "Ese horario acaba de ser reservado por otro paciente. Elige otro turno.");
        }
        if (citas.contarCruzadasDelPaciente(paciente.getId(), inicio, fin, EstadoCita.CANCELADA) > 0) {
            throw new ConflictoReservaException("Ya tienes una cita en ese horario. Revisa «Mis citas».");
        }

        // Si el turno lo ocupó antes una cita CANCELADA, se reutiliza su fila (UNIQUE medico + inicio).
        Cita cita = citas.findByMedicoIdAndFechaHoraInicio(medico.getId(), inicio).orElseGet(Cita::new);
        cita.setPaciente(paciente);
        cita.setMedico(medico);
        cita.setFechaHoraInicio(inicio);
        cita.setFechaHoraFin(fin);
        cita.setModalidad(modalidad);
        cita.setMotivo(motivo);
        cita.setEstado(EstadoCita.PENDIENTE);
        cita.setMotivoCancelacion(null);
        cita.setPrecio(medico.precioPara(modalidad));
        cita = citas.saveAndFlush(cita);

        // Persiste el saludo del médico en la conversación dentro de la transacción de la reserva.
        mensajeria.iniciarPorReserva(paciente, medico);

        return new ReservaCreada(cita.getId(), codigo(cita.getId()),
                DoctorProfileService.nombrePublico(medico.getUsuario()),
                capitalizar(inicio.format(FECHA)), inicio.format(HORA) + " – " + fin.format(HORA),
                modalidad == Modalidad.TELECONSULTA ? "Teleconsulta" : "Presencial", "Pendiente");
    }

    /** "MED-000123": el mismo formato que mostraba la demo, ahora con el id real de la cita. */
    public static String codigo(Long id) {
        return String.format("MED-%06d", id);
    }

    // ------------------------------------------------------------------
    // Validación
    // ------------------------------------------------------------------
    private Usuario pacienteActivo(String email) {
        // Con bloqueo: dos reservas simultáneas de la misma cuenta se atienden una tras otra.
        Usuario u = usuarios.bloquearPorEmail(email == null ? "" : email.trim().toLowerCase())
                .orElseThrow(() -> new NegocioException("Inicia sesión como paciente para reservar una cita."));
        if (!"PACIENTE".equals(u.getRol().getNombre()) || u.getEstado() != EstadoUsuario.ACTIVO) {
            throw new NegocioException("Solo las cuentas de paciente pueden reservar citas.");
        }
        return u;
    }

    /**
     * La franja activa que genera ese turno. Misma lógica que DoctorProfileService.generarTurnos: el turno
     * empieza en horaInicio + k × duración y debe terminar a más tardar a la horaFin de la franja.
     */
    private HorarioAtencion franjaDelTurno(Long medicoId, LocalDate fecha, LocalTime hora, Modalidad modalidad) {
        int dia = fecha.getDayOfWeek().getValue();
        return horarios.findByMedicoIdAndActivoTrue(medicoId).stream()
                .filter(h -> h.getDiaSemana() == dia)
                .filter(h -> h.getModalidad() == ModalidadAtencion.AMBAS
                        || h.getModalidad().name().equals(modalidad.name()))
                .filter(h -> h.getDuracionCitaMin() > 0)
                .filter(h -> !hora.isBefore(h.getHoraInicio()))
                .filter(h -> {
                    LocalTime fin = hora.plusMinutes(h.getDuracionCitaMin());
                    return fin.isAfter(hora) && !fin.isAfter(h.getHoraFin()); // no cruza medianoche
                })
                .filter(h -> Duration.between(h.getHoraInicio(), hora).toMinutes() % h.getDuracionCitaMin() == 0)
                .min(Comparator.comparing(HorarioAtencion::getHoraInicio))
                .orElseThrow(() -> new NegocioException(
                        "Ese horario no está disponible para este médico en la modalidad elegida."));
    }

    private Long entero(String raw) {
        try {
            return Long.valueOf(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            throw new NegocioException("El médico indicado no es válido.");
        }
    }

    private LocalDate fecha(String raw) {
        try {
            return LocalDate.parse(raw == null ? "" : raw.trim());
        } catch (DateTimeParseException e) {
            throw new NegocioException("La fecha de la cita no es válida.");
        }
    }

    private LocalTime hora(String raw) {
        try {
            LocalTime t = LocalTime.parse(raw == null ? "" : raw.trim());
            if (!t.equals(t.truncatedTo(ChronoUnit.MINUTES))) throw new DateTimeParseException("", "", 0);
            return t;
        } catch (DateTimeParseException e) {
            throw new NegocioException("La hora de la cita no es válida (usa el formato HH:mm).");
        }
    }

    private Modalidad modalidad(String raw) {
        try {
            return Modalidad.valueOf(raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NegocioException("Selecciona una modalidad válida (presencial o teleconsulta).");
        }
    }

    private String motivo(String raw) {
        String m = raw == null ? "" : raw.trim();
        if (m.length() > MOTIVO_MAX) {
            throw new NegocioException("El motivo de consulta no puede superar " + MOTIVO_MAX + " caracteres.");
        }
        return m.isEmpty() ? null : m;
    }

    private static String capitalizar(String s) {
        return s == null || s.isBlank() ? "" : s.substring(0, 1).toUpperCase(ES) + s.substring(1);
    }
}
