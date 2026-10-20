package utp.edu.pe.recomendaciones.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.dto.AdminDataView;
import utp.edu.pe.recomendaciones.dto.AdminDataView.ActivityView;
import utp.edu.pe.recomendaciones.dto.AdminDataView.ApplicationView;
import utp.edu.pe.recomendaciones.dto.AdminDataView.DoctorView;
import utp.edu.pe.recomendaciones.dto.AdminDataView.PatientView;
import utp.edu.pe.recomendaciones.dto.DecisionMedico;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Auditoria;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.SolicitudVerificacion;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.AccionAuditoria;
import utp.edu.pe.recomendaciones.model.enums.EstadoSolicitud;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.repository.AuditoriaRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.repository.SolicitudVerificacionRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

@Service
public class AdminService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SolicitudVerificacionRepository solicitudes;
    private final MedicoRepository medicos;
    private final UsuarioRepository usuarios;
    private final AuditoriaRepository auditoria;

    public AdminService(SolicitudVerificacionRepository solicitudes, MedicoRepository medicos,
                        UsuarioRepository usuarios, AuditoriaRepository auditoria) {
        this.solicitudes = solicitudes;
        this.medicos = medicos;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public AdminDataView datos() {
        List<ApplicationView> apps = solicitudes
                .findAll(Sort.by(Sort.Direction.DESC, "fechaSolicitud")).stream()
                .map(this::toApplication)
                .toList();

        List<DoctorView> docs = medicos.findAll(Sort.by("id")).stream()
                .filter(m -> m.getUsuario().getEstado() == EstadoUsuario.ACTIVO
                        || m.getUsuario().getEstado() == EstadoUsuario.SUSPENDIDO)
                .map(this::toDoctor)
                .toList();

        long pacientes = usuarios.findByRolNombre("PACIENTE").stream()
                .filter(u -> u.getEstado() != EstadoUsuario.ELIMINADO)
                .count();

        List<ActivityView> actividad = auditoria.findTop50ByOrderByFechaDesc().stream()
                .limit(6)
                .map(this::toActivity)
                .toList();

        List<PatientView> listaPacientes = usuarios.findByRolNombre("PACIENTE").stream()
                .filter(u -> u.getEstado() == EstadoUsuario.ACTIVO || u.getEstado() == EstadoUsuario.SUSPENDIDO)
                .sorted(java.util.Comparator.comparing(Usuario::getId).reversed())
                .map(this::toPatient)
                .toList();

        return new AdminDataView(apps, docs, pacientes, actividad, listaPacientes);
    }

    // ------------------------------------------------------------------
    // Solicitudes
    // ------------------------------------------------------------------
    /** Aprueba la solicitud. Devuelve la decisión para que quien llama avise al médico por correo. */
    @Transactional
    public DecisionMedico aprobar(Long solicitudId, String adminEmail) {
        SolicitudVerificacion s = solicitudParaRevisar(solicitudId);
        if (s.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new NegocioException("Esta solicitud ya fue revisada.");
        }
        Usuario admin = admin(adminEmail);
        Medico m = s.getMedico();
        Usuario u = m.getUsuario();

        s.setEstado(EstadoSolicitud.APROBADA);
        s.setMotivoRechazo(null);
        s.setRevisadoPor(admin);
        s.setFechaRevision(LocalDateTime.now());
        solicitudes.save(s);

        u.setEstado(EstadoUsuario.ACTIVO);
        usuarios.save(u);

        registrar(admin, AccionAuditoria.APROBAR_MEDICO, m,
                "Solicitud aprobada: " + nombre(u) + " (CMP " + m.getCmp() + ")");
        return new DecisionMedico(true, u.getEmail(), nombre(u), null);
    }

    /** Rechaza la solicitud. Devuelve la decisión para que quien llama avise al médico por correo. */
    @Transactional
    public DecisionMedico rechazar(Long solicitudId, String motivo, String adminEmail) {
        String razon = motivo == null ? "" : motivo.trim();
        if (razon.isEmpty()) {
            throw new NegocioException("Escribe el motivo del rechazo.");
        }
        if (razon.length() > 500) {
            throw new NegocioException("El motivo no puede superar 500 caracteres.");
        }
        SolicitudVerificacion s = solicitudParaRevisar(solicitudId);
        if (s.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new NegocioException("Esta solicitud ya fue revisada.");
        }
        Usuario admin = admin(adminEmail);
        Medico m = s.getMedico();

        s.setEstado(EstadoSolicitud.RECHAZADA);
        s.setMotivoRechazo(razon);
        s.setRevisadoPor(admin);
        s.setFechaRevision(LocalDateTime.now());
        solicitudes.save(s);
        // El usuario permanece PENDIENTE: no puede iniciar sesión.

        registrar(admin, AccionAuditoria.RECHAZAR_MEDICO, m,
                "Solicitud rechazada: " + nombre(m.getUsuario()) + ". Motivo: " + razon);
        return new DecisionMedico(false, m.getUsuario().getEmail(), nombre(m.getUsuario()), razon);
    }

    // ------------------------------------------------------------------
    // Cuentas de médicos
    // ------------------------------------------------------------------
    @Transactional
    public void suspender(Long medicoId, String adminEmail) {
        Medico m = medico(medicoId);
        Usuario u = m.getUsuario();
        if (u.getEstado() != EstadoUsuario.ACTIVO) {
            throw new NegocioException("Solo se puede suspender una cuenta activa.");
        }
        u.setEstado(EstadoUsuario.SUSPENDIDO);
        usuarios.save(u);
        registrar(admin(adminEmail), AccionAuditoria.SUSPENDER_MEDICO, m, "Cuenta suspendida: " + nombre(u));
    }

    @Transactional
    public void reactivar(Long medicoId, String adminEmail) {
        Medico m = medico(medicoId);
        Usuario u = m.getUsuario();
        if (u.getEstado() != EstadoUsuario.SUSPENDIDO) {
            throw new NegocioException("Solo se puede reactivar una cuenta suspendida.");
        }
        u.setEstado(EstadoUsuario.ACTIVO);
        usuarios.save(u);
        registrar(admin(adminEmail), AccionAuditoria.REACTIVAR_MEDICO, m, "Cuenta reactivada: " + nombre(u));
    }

    /** Borrado lógico: el registro se conserva con estado ELIMINADO. */
    @Transactional
    public void eliminar(Long medicoId, String adminEmail) {
        Medico m = medico(medicoId);
        Usuario u = m.getUsuario();
        if (u.getEstado() == EstadoUsuario.ELIMINADO) {
            throw new NegocioException("La cuenta ya fue eliminada.");
        }
        u.setEstado(EstadoUsuario.ELIMINADO);
        usuarios.save(u);
        registrar(admin(adminEmail), AccionAuditoria.ELIMINAR_MEDICO, m, "Cuenta eliminada: " + nombre(u));
    }

    // ------------------------------------------------------------------
    // Cuentas de pacientes
    // ------------------------------------------------------------------
    /** Suspende a un paciente: no podrá iniciar sesión hasta que se reactive. */
    @Transactional
    public void suspenderPaciente(Long usuarioId, String adminEmail) {
        Usuario u = paciente(usuarioId);
        if (u.getEstado() != EstadoUsuario.ACTIVO) {
            throw new NegocioException("Solo se puede suspender una cuenta activa.");
        }
        u.setEstado(EstadoUsuario.SUSPENDIDO);
        usuarios.save(u);
        registrarPaciente(admin(adminEmail), AccionAuditoria.SUSPENDER_MEDICO, u,
                "Paciente suspendido: " + nombre(u));
    }

    @Transactional
    public void reactivarPaciente(Long usuarioId, String adminEmail) {
        Usuario u = paciente(usuarioId);
        if (u.getEstado() != EstadoUsuario.SUSPENDIDO) {
            throw new NegocioException("Solo se puede reactivar una cuenta suspendida.");
        }
        u.setEstado(EstadoUsuario.ACTIVO);
        usuarios.save(u);
        registrarPaciente(admin(adminEmail), AccionAuditoria.REACTIVAR_MEDICO, u,
                "Paciente reactivado: " + nombre(u));
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------
    /** Solo cuentas con rol PACIENTE: este flujo nunca debe tocar administradores ni médicos. */
    private Usuario paciente(Long id) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new NegocioException("El paciente no existe."));
        if (u.getRol() == null || !"PACIENTE".equals(u.getRol().getNombre())) {
            throw new NegocioException("El paciente no existe.");
        }
        return u;
    }

    /** Lee la solicitud bloqueando su fila: evita decidir dos veces la misma (y enviar dos correos). */
    private SolicitudVerificacion solicitudParaRevisar(Long id) {
        return solicitudes.buscarParaRevisar(id).orElseThrow(() -> new NegocioException("La solicitud no existe."));
    }

    private Medico medico(Long id) {
        return medicos.findById(id).orElseThrow(() -> new NegocioException("El médico no existe."));
    }

    private Usuario admin(String email) {
        return usuarios.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new NegocioException("No se encontró la cuenta del administrador."));
    }

    private void registrar(Usuario admin, AccionAuditoria accion, Medico m, String detalle) {
        Auditoria a = new Auditoria();
        a.setUsuario(admin);
        a.setAccion(accion);
        a.setEntidad("Medico");
        a.setEntidadId(m.getId());
        a.setDetalle(detalle.length() > 500 ? detalle.substring(0, 500) : detalle);
        a.setFecha(LocalDateTime.now());
        auditoria.save(a);
    }

    /**
     * Auditoría de pacientes. Reutiliza SUSPENDER_MEDICO / REACTIVAR_MEDICO con entidad «Paciente» para no
     * ampliar el ENUM de la columna accion (ddl-auto=update no modifica enums ya creados en la BD).
     */
    private void registrarPaciente(Usuario admin, AccionAuditoria accion, Usuario paciente, String detalle) {
        Auditoria a = new Auditoria();
        a.setUsuario(admin);
        a.setAccion(accion);
        a.setEntidad("Paciente");
        a.setEntidadId(paciente.getId());
        a.setDetalle(detalle.length() > 500 ? detalle.substring(0, 500) : detalle);
        a.setFecha(LocalDateTime.now());
        auditoria.save(a);
    }

    private PatientView toPatient(Usuario u) {
        return new PatientView(u.getId(), nombre(u), u.getEmail(), u.getDni(), vacio(u.getTelefono()),
                u.getCreadoEn() != null ? u.getCreadoEn().format(FECHA) : "",
                u.getUltimoAcceso() != null ? u.getUltimoAcceso().format(FECHA_HORA) : "Nunca",
                u.getEstado().name());
    }

    private String nombre(Usuario u) {
        return u.getNombres() + " " + u.getApellidos();
    }

    private String vacio(String s) {
        return s == null ? "" : s;
    }

    private ApplicationView toApplication(SolicitudVerificacion s) {
        Medico m = s.getMedico();
        Usuario u = m.getUsuario();
        return new ApplicationView(
                s.getId(), nombre(u), u.getEmail(), u.getDni(), u.getTelefono(),
                m.getCmp(), vacio(m.getRne()), vacio(m.getTituloProfesional()), vacio(m.getUniversidad()),
                m.getAnioEgreso(), m.getAniosExperiencia(),
                m.getEspecialidad().getNombre(),
                m.getEstablecimiento() != null ? m.getEstablecimiento().getNombre() : "",
                s.getSustentoUrl(), s.getFechaSolicitud().format(FECHA),
                s.getEstado().name(), vacio(s.getMotivoRechazo()));
    }

    private DoctorView toDoctor(Medico m) {
        Usuario u = m.getUsuario();
        return new DoctorView(
                m.getId(), nombre(u), u.getEmail(), m.getEspecialidad().getNombre(),
                m.getEstablecimiento() != null ? m.getEstablecimiento().getNombre() : "Independiente",
                m.getRatingPromedio(), u.getEstado().name());
    }

    private ActivityView toActivity(Auditoria a) {
        String titulo;
        String color;
        switch (a.getAccion()) {
            case APROBAR_MEDICO -> { titulo = "Cuenta aprobada"; color = "green"; }
            case RECHAZAR_MEDICO -> { titulo = "Solicitud rechazada"; color = "red"; }
            case SUSPENDER_MEDICO -> { titulo = "Cuenta suspendida"; color = "amber"; }
            case REACTIVAR_MEDICO -> { titulo = "Cuenta reactivada"; color = "green"; }
            case ELIMINAR_MEDICO -> { titulo = "Cuenta eliminada"; color = "red"; }
            default -> { titulo = a.getAccion().name(); color = ""; }
        }
        return new ActivityView(titulo, vacio(a.getDetalle()), color, a.getFecha().format(FECHA_HORA));
    }
}
