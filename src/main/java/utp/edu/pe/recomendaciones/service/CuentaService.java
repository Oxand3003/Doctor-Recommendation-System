package utp.edu.pe.recomendaciones.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.dto.AccountView;
import utp.edu.pe.recomendaciones.dto.AccountView.CitaItem;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Cita;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.EstadoCita;
import utp.edu.pe.recomendaciones.model.enums.Modalidad;
import utp.edu.pe.recomendaciones.repository.CitaRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;
import utp.edu.pe.recomendaciones.repository.ValoracionRepository;

@Service
public class CuentaService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEE d 'de' MMM 'de' yyyy", ES);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final UsuarioRepository usuarios;
    private final MedicoRepository medicos;
    private final CitaRepository citas;
    private final CitaEstadoService estados;
    private final ValoracionRepository valoraciones;

    public CuentaService(UsuarioRepository usuarios, MedicoRepository medicos, CitaRepository citas,
                         CitaEstadoService estados, ValoracionRepository valoraciones) {
        this.usuarios = usuarios;
        this.medicos = medicos;
        this.citas = citas;
        this.estados = estados;
        this.valoraciones = valoraciones;
    }

    @Transactional(readOnly = true)
    public AccountView cuenta(String email) {
        Usuario u = usuarios.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new NegocioException("Cuenta no encontrada."));
        String rol = u.getRol().getNombre();
        boolean paciente = "PACIENTE".equals(rol);
        boolean admin = "ADMIN".equals(rol);

        Long medicoId = "MEDICO".equals(rol)
                ? medicos.findByUsuarioId(u.getId()).map(m -> m.getId()).orElse(null)
                : null;

        List<CitaItem> pendientes = new ArrayList<>();
        List<CitaItem> historial = new ArrayList<>();
        if (paciente) {
            estados.marcarAtendidas();
            LocalDateTime ahora = LocalDateTime.now(LIMA);
            // La consulta llega de la más reciente a la más antigua.
            for (Cita c : citas.delPacienteConMedico(u.getId())) {
                (esPendiente(c, ahora) ? pendientes : historial).add(toItem(c));
            }
            // Las pendientes se leen de la más próxima a la más lejana.
            java.util.Collections.reverse(pendientes);
        }

        String rolLabel = admin ? "Administrador" : paciente ? "Paciente" : "Médico";
        return new AccountView(u.getNombres() + " " + u.getApellidos(), rolLabel, u.getEmail(), u.getDni(),
                u.getTelefono(), u.getEstado().name(), medicoId, paciente, admin, pendientes, historial);
    }

    /** Pendiente = aún por atender (PENDIENTE o CONFIRMADA) y que todavía no terminó. */
    static boolean esPendiente(Cita c, LocalDateTime ahora) {
        boolean vigente = c.getEstado() == EstadoCita.PENDIENTE || c.getEstado() == EstadoCita.CONFIRMADA;
        return vigente && c.getFechaHoraFin().isAfter(ahora);
    }

    private CitaItem toItem(Cita c) {
        Medico m = c.getMedico();
        boolean tele = c.getModalidad() == Modalidad.TELECONSULTA;
        String lugar = tele ? "Atención virtual"
                : m.getEstablecimiento() != null ? m.getEstablecimiento().getNombre() : "Sede por confirmar";
        String motivo = c.getMotivo() == null || c.getMotivo().isBlank() ? null : c.getMotivo();
        return new CitaItem(
                c.getId(),
                m.getId(),
                CitaPacienteService.codigo(c.getId()),
                DoctorProfileService.nombrePublico(m.getUsuario()),
                m.getEspecialidad().getNombre(),
                capitalizar(c.getFechaHoraInicio().format(FECHA)),
                c.getFechaHoraInicio().format(HORA) + " – " + c.getFechaHoraFin().format(HORA),
                tele ? "Teleconsulta" : "Presencial",
                lugar,
                etiqueta(c.getEstado()),
                c.getEstado().name().toLowerCase(Locale.ROOT).replace('_', '-'),
                motivo,
                c.getEstado() == EstadoCita.CANCELADA && c.getMotivoCancelacion() != null
                        && !c.getMotivoCancelacion().isBlank() ? c.getMotivoCancelacion() : null,
                valoraciones.findByCitaId(c.getId()).map(utp.edu.pe.recomendaciones.model.Valoracion::getId).orElse(null),
                valoraciones.findByCitaId(c.getId()).map(utp.edu.pe.recomendaciones.model.Valoracion::getComentario).orElse(null),
                c.getEstado() == EstadoCita.COMPLETADA && !valoraciones.existsByCitaId(c.getId()));
    }

    private static String etiqueta(EstadoCita e) {
        return switch (e) {
            case PENDIENTE -> "Pendiente";
            case CONFIRMADA -> "Confirmada";
            case CANCELADA -> "Cancelada";
            case COMPLETADA -> "Atendida";
            case NO_ASISTIO -> "No asistió";
        };
    }

    private static String capitalizar(String s) {
        return s == null || s.isBlank() ? "" : s.substring(0, 1).toUpperCase(ES) + s.substring(1);
    }
}
