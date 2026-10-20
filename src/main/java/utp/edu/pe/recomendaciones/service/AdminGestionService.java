package utp.edu.pe.recomendaciones.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import utp.edu.pe.recomendaciones.dto.AdminGestionView;
import utp.edu.pe.recomendaciones.dto.AdminGestionView.AdminFila;
import utp.edu.pe.recomendaciones.dto.AdminGestionView.SolicitudFila;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.SolicitudDesactivacionAdmin;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.VotoDesactivacionAdmin;
import utp.edu.pe.recomendaciones.model.enums.EstadoDesactivacionAdmin;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.repository.SolicitudDesactivacionAdminRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;
import utp.edu.pe.recomendaciones.repository.VotoDesactivacionAdminRepository;

/**
 * Gestión de las cuentas de administrador: listado y desactivación por consenso.
 * <p>
 * Reglas de seguridad:
 * <ul>
 *   <li>Cualquier administrador ACTIVO puede iniciar una solicitud de desactivación contra OTRO administrador.
 *       Quien la inicia cuenta como el primer voto.</li>
 *   <li>La cuenta afectada no puede iniciar, votar, rechazar ni cancelar nada sobre sí misma.</li>
 *   <li>Cuando los votos de administradores distintos llegan al mínimo (2 por defecto) la cuenta pasa a
 *       SUSPENDIDO y ya no puede iniciar sesión (ni seguir usando una sesión abierta).</li>
 *   <li>Como la cuenta afectada no vota, hacen falta al menos votos+1 (3) administradores activos para iniciarla, y
 *       nunca pueden quedar menos de 2 administradores activos tras una suspensión.</li>
 *   <li>Cualquier otro administrador puede rechazarla (queda cerrada); quien la inició puede cancelarla.</li>
 * </ul>
 */
@Service
public class AdminGestionService {
    private static final Logger log = LoggerFactory.getLogger(AdminGestionService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);
    static final int MIN_ACTIVOS_TRAS_SUSPENDER = 2;

    private final UsuarioRepository usuarios;
    private final SolicitudDesactivacionAdminRepository solicitudes;
    private final VotoDesactivacionAdminRepository votos;
    private final CorreoService correo;
    private final int votosRequeridos;

    public AdminGestionService(UsuarioRepository usuarios, SolicitudDesactivacionAdminRepository solicitudes,
                               VotoDesactivacionAdminRepository votos, CorreoService correo,
                               @Value("${medicerca.admin.desactivacion.votos-requeridos:2}") int votosRequeridos) {
        this.usuarios = usuarios;
        this.solicitudes = solicitudes;
        this.votos = votos;
        this.correo = correo;
        this.votosRequeridos = Math.max(2, votosRequeridos);
    }

    // ------------------------------------------------------------------ consulta

    @Transactional(readOnly = true)
    public AdminGestionView ver(String actorEmail) {
        Usuario actor = adminActivo(actorEmail);
        List<Usuario> todos = administradores();
        int activos = (int) todos.stream().filter(u -> u.getEstado() == EstadoUsuario.ACTIVO).count();
        boolean puedeIniciar = activos >= votosRequeridos + 1;

        List<SolicitudDesactivacionAdmin> pendientes = solicitudes.findByEstadoOrderByCreadoEnDesc(EstadoDesactivacionAdmin.PENDIENTE);
        List<Long> conSolicitud = pendientes.stream().map(s -> s.getObjetivo().getId()).toList();

        List<AdminFila> filas = new ArrayList<>();
        for (Usuario u : todos) {
            boolean activo = u.getEstado() == EstadoUsuario.ACTIVO;
            boolean yo = u.getId().equals(actor.getId());
            boolean con = conSolicitud.contains(u.getId());
            filas.add(new AdminFila(u.getId(), u.getEmail(), u.getEstado().name(), fecha(u.getCreadoEn()),
                    u.getUltimoAcceso() == null ? "Nunca" : fecha(u.getUltimoAcceso()), yo, con,
                    activo && !yo && !con && puedeIniciar));
        }
        List<SolicitudFila> pend = pendientes.stream().map(s -> fila(s, actor)).toList();
        List<SolicitudFila> hist = solicitudes.findTop10ByEstadoNotOrderByResueltoEnDesc(EstadoDesactivacionAdmin.PENDIENTE)
                .stream().map(s -> fila(s, actor)).toList();
        return new AdminGestionView(activos, todos.size() - activos, votosRequeridos, puedeIniciar, filas, pend, hist);
    }

    // ------------------------------------------------------------------ acciones

    /** Inicia la solicitud; el iniciador cuenta como el primer voto. */
    @Transactional
    public void solicitarDesactivacion(String actorEmail, Long objetivoId, String motivo) {
        Usuario actor = adminActivo(actorEmail);
        Usuario objetivo = usuarios.findById(objetivoId == null ? -1L : objetivoId)
                .filter(this::esAdmin).orElseThrow(() -> new NegocioException("La cuenta de administrador no existe."));
        if (objetivo.getId().equals(actor.getId())) {
            throw new NegocioException("No puedes solicitar la desactivación de tu propia cuenta.");
        }
        if (objetivo.getEstado() != EstadoUsuario.ACTIVO) {
            throw new NegocioException("Esa cuenta de administrador ya no está activa.");
        }
        if (activos() < votosRequeridos + 1) {
            throw new NegocioException("Se necesitan al menos " + (votosRequeridos + 1)
                    + " administradores activos: la cuenta afectada no puede votar y hacen falta " + votosRequeridos + " votos.");
        }
        if (solicitudes.existsByObjetivoIdAndEstado(objetivo.getId(), EstadoDesactivacionAdmin.PENDIENTE)) {
            throw new NegocioException("Ya hay una solicitud de desactivación pendiente para esa cuenta.");
        }
        String razon = motivo == null ? "" : motivo.trim();
        if (razon.length() < 5) throw new NegocioException("Escribe el motivo de la desactivación (mínimo 5 caracteres).");
        if (razon.length() > 300) razon = razon.substring(0, 300);

        SolicitudDesactivacionAdmin s = new SolicitudDesactivacionAdmin();
        s.setObjetivo(objetivo);
        s.setIniciador(actor);
        s.setMotivo(razon);
        solicitudes.save(s);
        registrarVoto(s, actor);

        String aviso = "<p>El administrador <b>" + esc(actor.getEmail()) + "</b> solicitó desactivar la cuenta <b>"
                + esc(objetivo.getEmail()) + "</b>.</p><p>Motivo: " + esc(razon) + "</p><p>Necesita " + votosRequeridos
                + " votos de administradores distintos. Entra a Gestión de administradores para aprobarla o rechazarla.</p>";
        administradores().stream()
                .filter(u -> u.getEstado() == EstadoUsuario.ACTIVO && !u.getId().equals(actor.getId())
                        && !u.getId().equals(objetivo.getId()))
                .forEach(u -> avisar(u.getEmail(), "Solicitud de desactivación de un administrador", aviso));
    }

    /** Suma el voto del administrador. Devuelve un mensaje para mostrar (si se ejecutó o cuántos votos faltan). */
    @Transactional
    public String aprobar(String actorEmail, Long solicitudId) {
        Usuario actor = adminActivo(actorEmail);
        SolicitudDesactivacionAdmin s = pendiente(solicitudId);
        Usuario objetivo = s.getObjetivo();
        if (objetivo.getId().equals(actor.getId())) {
            throw new NegocioException("No puedes votar sobre la desactivación de tu propia cuenta.");
        }
        if (objetivo.getEstado() != EstadoUsuario.ACTIVO) {
            cerrar(s, EstadoDesactivacionAdmin.CANCELADA, actor);
            return "La cuenta ya no estaba activa; la solicitud se cerró.";
        }
        if (votos.existsBySolicitudIdAndVotanteId(s.getId(), actor.getId())) {
            throw new NegocioException("Ya votaste en esta solicitud.");
        }
        registrarVoto(s, actor);
        long total = votos.countBySolicitudId(s.getId());
        if (total < votosRequeridos) {
            return "Voto registrado (" + total + " de " + votosRequeridos + "). Falta" + (votosRequeridos - total == 1 ? "" : "n")
                    + " " + (votosRequeridos - total) + " voto" + (votosRequeridos - total == 1 ? "" : "s") + ".";
        }
        if (activos() - 1 < MIN_ACTIVOS_TRAS_SUSPENDER) {
            throw new NegocioException("No se puede suspender: deben quedar al menos " + MIN_ACTIVOS_TRAS_SUSPENDER
                    + " administradores activos.");
        }
        objetivo.setEstado(EstadoUsuario.SUSPENDIDO);
        usuarios.save(objetivo);
        cerrar(s, EstadoDesactivacionAdmin.EJECUTADA, actor);
        avisar(objetivo.getEmail(), "Tu cuenta de administrador fue suspendida",
                "<p>Tu cuenta de administrador en MediCerca fue suspendida por decisión de " + votosRequeridos
                        + " administradores.</p><p>Motivo: " + esc(s.getMotivo()) + "</p>");
        return "La cuenta " + objetivo.getEmail() + " fue suspendida (" + total + " de " + votosRequeridos + " votos).";
    }

    /** Cualquier administrador distinto de la cuenta afectada puede rechazarla; la solicitud queda cerrada. */
    @Transactional
    public void rechazar(String actorEmail, Long solicitudId) {
        Usuario actor = adminActivo(actorEmail);
        SolicitudDesactivacionAdmin s = pendiente(solicitudId);
        if (s.getObjetivo().getId().equals(actor.getId())) {
            throw new NegocioException("No puedes intervenir en la solicitud sobre tu propia cuenta.");
        }
        cerrar(s, EstadoDesactivacionAdmin.RECHAZADA, actor);
    }

    /** Solo quien inició la solicitud puede cancelarla. */
    @Transactional
    public void cancelar(String actorEmail, Long solicitudId) {
        Usuario actor = adminActivo(actorEmail);
        SolicitudDesactivacionAdmin s = pendiente(solicitudId);
        if (!s.getIniciador().getId().equals(actor.getId())) {
            throw new NegocioException("Solo quien inició la solicitud puede cancelarla.");
        }
        cerrar(s, EstadoDesactivacionAdmin.CANCELADA, actor);
    }

    // ------------------------------------------------------------------ internos

    private SolicitudDesactivacionAdmin pendiente(Long id) {
        SolicitudDesactivacionAdmin s = solicitudes.bloquearPorId(id == null ? -1L : id)
                .orElseThrow(() -> new NegocioException("La solicitud no existe."));
        if (s.getEstado() != EstadoDesactivacionAdmin.PENDIENTE) {
            throw new NegocioException("Esa solicitud ya fue resuelta.");
        }
        return s;
    }

    private void cerrar(SolicitudDesactivacionAdmin s, EstadoDesactivacionAdmin estado, Usuario por) {
        s.setEstado(estado);
        s.setResueltoPor(por);
        s.setResueltoEn(LocalDateTime.now());
        solicitudes.save(s);
    }

    private void registrarVoto(SolicitudDesactivacionAdmin s, Usuario votante) {
        VotoDesactivacionAdmin v = new VotoDesactivacionAdmin();
        v.setSolicitud(s);
        v.setVotante(votante);
        votos.save(v);
    }

    private SolicitudFila fila(SolicitudDesactivacionAdmin s, Usuario actor) {
        List<VotoDesactivacionAdmin> lista = votos.findBySolicitudIdOrderByCreadoEnAsc(s.getId());
        boolean pendiente = s.getEstado() == EstadoDesactivacionAdmin.PENDIENTE;
        boolean esObjetivo = s.getObjetivo().getId().equals(actor.getId());
        boolean yaVote = lista.stream().anyMatch(v -> v.getVotante().getId().equals(actor.getId()));
        return new SolicitudFila(s.getId(), s.getObjetivo().getEmail(), s.getIniciador().getEmail(), s.getMotivo(),
                s.getEstado().name(), fecha(s.getCreadoEn()), s.getResueltoEn() == null ? null : fecha(s.getResueltoEn()),
                lista.size(), votosRequeridos, lista.stream().map(v -> v.getVotante().getEmail()).toList(), yaVote,
                pendiente && !esObjetivo && !yaVote, pendiente && !esObjetivo,
                pendiente && s.getIniciador().getId().equals(actor.getId()));
    }

    private List<Usuario> administradores() {
        return usuarios.findByRolNombre("ADMIN").stream()
                .sorted(Comparator.comparing(Usuario::getCreadoEn, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())))
                .toList();
    }

    private int activos() {
        return (int) administradores().stream().filter(u -> u.getEstado() == EstadoUsuario.ACTIVO).count();
    }

    private boolean esAdmin(Usuario u) {
        return u.getRol() != null && "ADMIN".equals(u.getRol().getNombre());
    }

    private Usuario adminActivo(String email) {
        Usuario u = email == null ? null : usuarios.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (u == null || !esAdmin(u) || u.getEstado() != EstadoUsuario.ACTIVO) {
            throw new NegocioException("La cuenta no tiene permisos administrativos.");
        }
        return u;
    }

    private String fecha(LocalDateTime f) {
        return f == null ? "" : f.format(FECHA);
    }

    private String esc(String texto) {
        return HtmlUtils.htmlEscape(texto == null ? "" : texto);
    }

    /** Los avisos por correo son un extra: si el correo no está configurado, la votación sigue valiendo. */
    private void avisar(String para, String asunto, String html) {
        try {
            correo.enviarHtml(para, asunto + " — MediCerca", "<div style='font-family:Arial;max-width:560px'><h2>MediCerca</h2>"
                    + html + "</div>");
        } catch (Exception ex) {
            log.warn("No se pudo avisar por correo a {}: {}", CorreoService.enmascarar(para), ex.getMessage());
        }
    }
}
