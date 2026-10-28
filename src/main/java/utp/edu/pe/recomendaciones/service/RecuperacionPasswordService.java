package utp.edu.pe.recomendaciones.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.CodigoSeguridad;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.PropositoCodigo;
import utp.edu.pe.recomendaciones.repository.CodigoSeguridadRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

/**
 * Recuperar el acceso cuando se olvidó la contraseña, SIN sesión iniciada. Mismo mecanismo de código de un
 * solo uso por correo que {@link CredencialesMedicoService}, pero el usuario se identifica por el correo que
 * escribe en el formulario (no por la sesión), porque todavía no puede entrar.
 *
 * Seguridad:
 *  - Si el correo no está registrado, no se revela: se responde igual que si se hubiera enviado el código
 *    (así nadie puede usar este formulario para averiguar qué correos existen en el sistema).
 *  - El código, su expiración, el tope de intentos y el tope de envíos son los mismos que en el resto del
 *    sistema (medicerca.security.code-*).
 *  - Al cambiar la contraseña se limpia también cualquier bloqueo por intentos fallidos de inicio de sesión.
 */
@Service
public class RecuperacionPasswordService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacionPasswordService.class);
    private static final Pattern SEIS_DIGITOS = Pattern.compile("^[0-9]{6}$");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final CodigoSeguridadRepository codigos;
    private final CorreoService correo;
    private final RegistroService registro;
    private final PasswordEncoder encoder;

    private final int vigenciaMinutos;
    private final int maxIntentos;
    private final int segundosEntreEnvios;
    private final int maxPorHora;

    public RecuperacionPasswordService(UsuarioRepository usuarios, CodigoSeguridadRepository codigos,
                                       CorreoService correo, RegistroService registro, PasswordEncoder encoder,
                                       @Value("${medicerca.security.code-ttl-minutes:10}") int vigenciaMinutos,
                                       @Value("${medicerca.security.code-max-attempts:5}") int maxIntentos,
                                       @Value("${medicerca.security.code-resend-seconds:60}") int segundosEntreEnvios,
                                       @Value("${medicerca.security.code-max-per-hour:5}") int maxPorHora) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.correo = correo;
        this.registro = registro;
        this.encoder = encoder;
        this.vigenciaMinutos = Math.max(1, vigenciaMinutos);
        this.maxIntentos = Math.max(1, maxIntentos);
        this.segundosEntreEnvios = Math.max(0, segundosEntreEnvios);
        this.maxPorHora = Math.max(1, maxPorHora);
    }

    /** Envía un código al correo si existe una cuenta con ese correo. Nunca revela si existe o no. */
    @Transactional
    public void solicitarCodigo(String email) {
        String limpio = registro.validarEmail(email);
        Optional<Usuario> usuario = usuarios.findByEmail(limpio);
        if (usuario.isEmpty()) {
            log.info("Recuperación de contraseña pedida para un correo no registrado: {}", CorreoService.enmascarar(limpio));
            return;
        }
        try {
            emitirCodigo(usuario.get());
        } catch (NegocioException e) {
            // Los límites de envío (espera entre pedidos, tope por hora) SÍ se comunican: no son
            // información sobre si el correo existe, son el mismo mensaje para cualquier cuenta.
            throw e;
        }
    }

    /** Verifica el código y, si es correcto, guarda la nueva contraseña y desbloquea la cuenta. */
    @Transactional(noRollbackFor = NegocioException.class)
    public void cambiarPassword(String email, String codigo, String nueva, String confirmacion) {
        String limpio = registro.validarEmail(email);
        Usuario u = usuarios.findByEmail(limpio)
                .orElseThrow(() -> new NegocioException("Código inválido o vencido. Solicita uno nuevo."));

        registro.validarContrasena(nueva);
        if (!nueva.equals(confirmacion)) {
            throw new NegocioException("Las contraseñas no coinciden.");
        }

        CodigoSeguridad c = verificar(u, codigo);

        u.setPasswordHash(encoder.encode(nueva));
        u.setIntentosFallidos(0);
        u.setBloqueadoHasta(null);
        usuarios.save(u);
        c.setConsumido(true);

        correo.enviarHtml(u.getEmail(), "Tu contraseña de MediCerca fue cambiada",
                marco("Tu contraseña fue cambiada",
                        "<p>La contraseña de tu cuenta en MediCerca se restableció correctamente. "
                                + "Si no fuiste tú, comunícate con el administrador de inmediato.</p>"));
    }

    // ------------------------------------------------------------------
    private void emitirCodigo(Usuario u) {
        LocalDateTime ahora = LocalDateTime.now();
        codigos.deleteByUsuarioIdAndExpiraEnBefore(u.getId(), ahora.minusDays(1));

        List<CodigoSeguridad> pendientes =
                codigos.findByUsuarioIdAndPropositoAndConsumidoFalse(u.getId(), PropositoCodigo.RECUPERAR_PASSWORD);

        Optional<CodigoSeguridad> ultimo =
                codigos.findFirstByUsuarioIdAndPropositoOrderByCreadoEnDesc(u.getId(), PropositoCodigo.RECUPERAR_PASSWORD);
        if (ultimo.isPresent() && ultimo.get().getCreadoEn().plusSeconds(segundosEntreEnvios).isAfter(ahora)) {
            long faltan = Duration.between(ahora, ultimo.get().getCreadoEn().plusSeconds(segundosEntreEnvios)).toSeconds() + 1;
            throw new NegocioException("Espera " + faltan + " segundos antes de pedir otro código.");
        }
        if (codigos.countByUsuarioIdAndCreadoEnAfter(u.getId(), ahora.minusHours(1)) >= maxPorHora) {
            throw new NegocioException("Pediste demasiados códigos. Inténtalo de nuevo en una hora.");
        }

        pendientes.forEach(p -> p.setConsumido(true));

        String codigo = String.format(Locale.ROOT, "%06d", ALEATORIO.nextInt(1_000_000));
        CodigoSeguridad c = new CodigoSeguridad();
        c.setUsuario(u);
        c.setProposito(PropositoCodigo.RECUPERAR_PASSWORD);
        c.setCodigoHash(encoder.encode(codigo));
        c.setIntentos(0);
        c.setConsumido(false);
        c.setCreadoEn(ahora);
        c.setExpiraEn(ahora.plusMinutes(vigenciaMinutos));
        codigos.save(c);

        correo.enviarHtml(u.getEmail(), "Recupera tu contraseña — MediCerca",
                marco("Recupera tu contraseña",
                        "<p>Recibimos una solicitud para <b>restablecer la contraseña</b> de tu cuenta en MediCerca.</p>"
                                + "<p style=\"font-size:32px;letter-spacing:8px;font-weight:700;color:#0F766E;margin:18px 0\">"
                                + codigo + "</p>"
                                + "<p>El código vence en <b>" + vigenciaMinutos + " minutos</b> y solo se puede usar una vez. "
                                + "No lo compartas con nadie.</p>"
                                + "<p style=\"color:#6b7280;font-size:13px\">Si no fuiste tú, ignora este mensaje: "
                                + "tu contraseña actual sigue siendo válida.</p>"));
        log.info("Código de recuperación de contraseña enviado a {}", CorreoService.enmascarar(u.getEmail()));
    }

    private CodigoSeguridad verificar(Usuario u, String ingresado) {
        List<CodigoSeguridad> activos =
                codigos.findByUsuarioIdAndPropositoAndConsumidoFalse(u.getId(), PropositoCodigo.RECUPERAR_PASSWORD);
        CodigoSeguridad c = activos.stream().max(Comparator.comparing(CodigoSeguridad::getCreadoEn))
                .orElseThrow(() -> new NegocioException("Primero solicita un código de recuperación."));

        if (c.getExpiraEn().isBefore(LocalDateTime.now())) {
            c.setConsumido(true);
            throw new NegocioException("El código caducó. Solicita uno nuevo.");
        }
        String limpio = ingresado == null ? "" : ingresado.trim();
        if (!SEIS_DIGITOS.matcher(limpio).matches()) {
            throw new NegocioException("Escribe el código de 6 dígitos que recibiste por correo.");
        }
        if (!encoder.matches(limpio, c.getCodigoHash())) {
            c.setIntentos(c.getIntentos() + 1);
            if (c.getIntentos() >= maxIntentos) {
                c.setConsumido(true);
                throw new NegocioException("Código incorrecto demasiadas veces. El código se anuló: solicita uno nuevo.");
            }
            int quedan = maxIntentos - c.getIntentos();
            throw new NegocioException("Código incorrecto. Te " + (quedan == 1 ? "queda 1 intento." : "quedan " + quedan + " intentos."));
        }
        return c;
    }

    private String marco(String titulo, String cuerpo) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;color:#1f2937;"
                + "line-height:1.55\"><h2 style=\"color:#0F766E\">" + titulo + "</h2>" + cuerpo
                + "<hr style=\"border:none;border-top:1px solid #e5e7eb;margin:24px 0\">"
                + "<p style=\"font-size:12px;color:#6b7280\">Mensaje automático de MediCerca.</p></div>";
    }
}
