package utp.edu.pe.recomendaciones.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpSession;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Auditoria;
import utp.edu.pe.recomendaciones.model.CodigoSeguridad;
import utp.edu.pe.recomendaciones.model.Rol;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.AccionAuditoria;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.model.enums.PropositoCodigo;
import utp.edu.pe.recomendaciones.repository.AuditoriaRepository;
import utp.edu.pe.recomendaciones.repository.CodigoSeguridadRepository;
import utp.edu.pe.recomendaciones.repository.RolRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

@Service
public class AdminAccessService {
    public static final String SESSION_VERIFIED = "MEDICERCA_ADMIN_2FA_VERIFIED";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern CODE = Pattern.compile("^[0-9]{8}$");

    private final UsuarioRepository usuarios;
    private final CodigoSeguridadRepository codigos;
    private final RolRepository roles;
    private final AuditoriaRepository auditoria;
    private final PasswordEncoder encoder;
    private final CorreoService correo;
    private final int ttlMinutes;
    private final int maxAttempts;
    private final int resendSeconds;

    public AdminAccessService(UsuarioRepository usuarios, CodigoSeguridadRepository codigos,
                              RolRepository roles, AuditoriaRepository auditoria,
                              PasswordEncoder encoder, CorreoService correo,
                              @Value("${medicerca.admin.2fa.ttl-minutes:10}") int ttlMinutes,
                              @Value("${medicerca.admin.2fa.max-attempts:5}") int maxAttempts,
                              @Value("${medicerca.admin.2fa.resend-seconds:60}") int resendSeconds) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.roles = roles;
        this.auditoria = auditoria;
        this.encoder = encoder;
        this.correo = correo;
        this.ttlMinutes = Math.max(1, ttlMinutes);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.resendSeconds = Math.max(0, resendSeconds);
    }

    @Transactional
    public void emitirCodigoLogin(String email) {
        Usuario admin = admin(email);
        emitir(admin, PropositoCodigo.ADMIN_LOGIN, admin.getEmail(), null,
                "Código de acceso administrativo", "Confirma tu inicio de sesión administrativo en MediCerca.");
    }

    @Transactional(noRollbackFor = NegocioException.class)
    public void verificarLogin(String email, String codigo, HttpSession session) {
        Usuario admin = admin(email);
        verificar(admin, PropositoCodigo.ADMIN_LOGIN, codigo);
        session.setAttribute(SESSION_VERIFIED, true);
    }

    @Transactional(readOnly = true)
    public boolean puedeGestionar(String email) {
        admin(email);          // todo administrador ACTIVO puede gestionar administradores
        return true;
    }

    @Transactional
    public void solicitarAlta(String actorEmail, String email, String password) {
        Usuario actor = admin(actorEmail);
        String nuevoEmail = normalizarEmail(email);
        if (usuarios.existsByEmail(nuevoEmail)) throw new NegocioException("Ese correo ya está registrado.");
        if (password == null || password.length() < 12) {
            throw new NegocioException("La contraseña administrativa debe tener al menos 12 caracteres.");
        }
        emitir(actor, PropositoCodigo.ADMIN_CREAR, nuevoEmail, encoder.encode(password),
                "Código para crear un administrador", "Confirma la creación de la cuenta administrativa para <b>"
                        + CorreoService.enmascarar(nuevoEmail) + "</b>.");
    }

    @Transactional(noRollbackFor = NegocioException.class)
    public void confirmarAlta(String actorEmail, String codigo) {
        Usuario actor = admin(actorEmail);
        CodigoSeguridad seguridad = verificar(actor, PropositoCodigo.ADMIN_CREAR, codigo);
        String email = normalizarEmail(seguridad.getNuevoEmail());
        if (usuarios.existsByEmail(email)) throw new NegocioException("Ese correo ya fue registrado.");
        Rol rol = roles.findByNombre("ADMIN").orElseThrow(() -> new NegocioException("El rol ADMIN no existe."));
        Usuario nuevo = new Usuario();
        nuevo.setNombres("Administrador");
        nuevo.setApellidos("MediCerca");
        nuevo.setDni("ADM" + String.format(Locale.ROOT, "%09d", RANDOM.nextInt(1_000_000_000)));
        nuevo.setEmail(email);
        nuevo.setTelefono("000000000");
        nuevo.setPasswordHash(seguridad.getValorPendiente());
        nuevo.setEstado(EstadoUsuario.ACTIVO);
        nuevo.setRol(rol);
        nuevo.setPuedeGestionarAdministradores(true);
        usuarios.save(nuevo);
        seguridad.setConsumido(true);
        Auditoria registro = new Auditoria();
        registro.setUsuario(actor);
        registro.setAccion(AccionAuditoria.CREAR_ADMIN);
        registro.setEntidad("Usuario");
        registro.setEntidadId(nuevo.getId());
        registro.setDetalle("Cuenta administrativa creada: " + email);
        auditoria.save(registro);
    }

    private void emitir(Usuario usuario, PropositoCodigo proposito, String destino,
                        String valorPendiente, String asunto, String motivo) {
        LocalDateTime ahora = LocalDateTime.now();
        List<CodigoSeguridad> pendientes = codigos.findByUsuarioIdAndPropositoAndConsumidoFalse(usuario.getId(), proposito);
        var ultimo = codigos.findFirstByUsuarioIdAndPropositoOrderByCreadoEnDesc(usuario.getId(), proposito);
        if (ultimo.isPresent() && ultimo.get().getCreadoEn().plusSeconds(resendSeconds).isAfter(ahora)) {
            long faltan = Duration.between(ahora, ultimo.get().getCreadoEn().plusSeconds(resendSeconds)).toSeconds() + 1;
            throw new NegocioException("Espera " + faltan + " segundos antes de reenviar el código.");
        }
        pendientes.forEach(c -> c.setConsumido(true));
        String code = String.format(Locale.ROOT, "%08d", RANDOM.nextInt(100_000_000));
        CodigoSeguridad seguridad = new CodigoSeguridad();
        seguridad.setUsuario(usuario);
        seguridad.setProposito(proposito);
        seguridad.setCodigoHash(encoder.encode(code));
        seguridad.setNuevoEmail(destino);
        seguridad.setValorPendiente(valorPendiente);
        seguridad.setIntentos(0);
        seguridad.setConsumido(false);
        seguridad.setCreadoEn(ahora);
        seguridad.setExpiraEn(ahora.plusMinutes(ttlMinutes));
        codigos.save(seguridad);
        correo.enviarHtml(destino, asunto + " — MediCerca", html(motivo, code));
    }

    private CodigoSeguridad verificar(Usuario usuario, PropositoCodigo proposito, String codigo) {
        CodigoSeguridad seguridad = codigos.findByUsuarioIdAndPropositoAndConsumidoFalse(usuario.getId(), proposito)
                .stream().max((a, b) -> a.getCreadoEn().compareTo(b.getCreadoEn()))
                .orElseThrow(() -> new NegocioException("Solicita un código nuevo."));
        LocalDateTime ahora = LocalDateTime.now();
        if (seguridad.getBloqueadoHasta() != null && seguridad.getBloqueadoHasta().isAfter(ahora)) {
            throw new NegocioException("Código bloqueado temporalmente. Solicita uno nuevo más tarde.");
        }
        if (seguridad.getExpiraEn().isBefore(ahora)) {
            seguridad.setConsumido(true);
            throw new NegocioException("El código caducó. Solicita uno nuevo.");
        }
        if (codigo == null || !CODE.matcher(codigo.trim()).matches() || !encoder.matches(codigo.trim(), seguridad.getCodigoHash())) {
            seguridad.setIntentos(seguridad.getIntentos() + 1);
            if (seguridad.getIntentos() >= maxAttempts) {
                seguridad.setConsumido(true);
                seguridad.setBloqueadoHasta(ahora.plusMinutes(15));
                throw new NegocioException("Demasiados intentos. El acceso quedó bloqueado temporalmente.");
            }
            throw new NegocioException("Código incorrecto. Intentos restantes: " + (maxAttempts - seguridad.getIntentos()));
        }
        seguridad.setConsumido(true);
        return seguridad;
    }

    private Usuario admin(String email) {
        Usuario usuario = usuarios.findByEmail(normalizarEmail(email))
                .orElseThrow(() -> new NegocioException("Cuenta administrativa no encontrada."));
        if (usuario.getRol() == null || !"ADMIN".equals(usuario.getRol().getNombre())
                || usuario.getEstado() != EstadoUsuario.ACTIVO) {
            throw new NegocioException("La cuenta no tiene permisos administrativos.");
        }
        return usuario;
    }

    private String normalizarEmail(String email) {
        if (email == null || !email.contains("@")) throw new NegocioException("El correo no es válido.");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String html(String motivo, String code) {
        return "<div style='font-family:Arial;max-width:560px'><h2>MediCerca</h2><p>" + motivo
                + "</p><p style='font-size:32px;letter-spacing:8px;font-weight:bold'>" + code
                + "</p><p>Vence en " + ttlMinutes + " minutos y solo puede usarse una vez.</p></div>";
    }
}
