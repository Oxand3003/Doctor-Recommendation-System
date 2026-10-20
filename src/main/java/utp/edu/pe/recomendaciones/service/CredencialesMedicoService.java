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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import utp.edu.pe.recomendaciones.dto.CredencialesView;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.CodigoSeguridad;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.PropositoCodigo;
import utp.edu.pe.recomendaciones.repository.CodigoSeguridadRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

/**
 * Cambio de credenciales (contraseña y correo de inicio de sesión) desde el perfil privado del médico.
 *
 * Seguridad:
 *  - El médico se identifica SIEMPRE por la sesión (correo → MedicoSesion). Ningún método recibe un id de usuario
 *    desde la web, así que nadie puede tocar las credenciales de otra cuenta; además, los códigos se buscan
 *    siempre por el usuario de la sesión, de modo que el código de otro médico tampoco sirve.
 *  - El código (6 dígitos, SecureRandom) se envía al correo REGISTRADO y se guarda solo como hash BCrypt.
 *  - Caduca (por defecto 10 min), admite pocos intentos (5) y es de un solo uso. Pedir uno nuevo anula el anterior.
 *  - Hay un tiempo mínimo entre envíos (60 s) y un tope por hora (5 códigos).
 *  - Los intentos fallidos SE GUARDAN aunque la operación lance NegocioException (noRollbackFor); por eso toda
 *    validación que no depende del código se hace antes de verificarlo y ninguna ruta de error modifica datos
 *    del usuario.
 */
@Service
public class CredencialesMedicoService {

    private static final Logger log = LoggerFactory.getLogger(CredencialesMedicoService.class);
    private static final Pattern SEIS_DIGITOS = Pattern.compile("^[0-9]{6}$");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final CodigoSeguridadRepository codigos;
    private final MedicoSesion medicoSesion;
    private final CorreoService correo;
    private final RegistroService registro;
    private final PasswordEncoder encoder;

    private final int vigenciaMinutos;
    private final int maxIntentos;
    private final int segundosEntreEnvios;
    private final int maxPorHora;

    public CredencialesMedicoService(UsuarioRepository usuarios, CodigoSeguridadRepository codigos,
                                     MedicoSesion medicoSesion, CorreoService correo, RegistroService registro,
                                     PasswordEncoder encoder,
                                     @Value("${medicerca.security.code-ttl-minutes:10}") int vigenciaMinutos,
                                     @Value("${medicerca.security.code-max-attempts:5}") int maxIntentos,
                                     @Value("${medicerca.security.code-resend-seconds:60}") int segundosEntreEnvios,
                                     @Value("${medicerca.security.code-max-per-hour:5}") int maxPorHora) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.medicoSesion = medicoSesion;
        this.correo = correo;
        this.registro = registro;
        this.encoder = encoder;
        this.vigenciaMinutos = Math.max(1, vigenciaMinutos);
        this.maxIntentos = Math.max(1, maxIntentos);
        this.segundosEntreEnvios = Math.max(0, segundosEntreEnvios);
        this.maxPorHora = Math.max(1, maxPorHora);
    }

    // ------------------------------------------------------------------
    // Lectura (para dibujar la sección del perfil)
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public CredencialesView estado(String email) {
        Usuario u = usuarioDe(email);
        LocalDateTime ahora = LocalDateTime.now();
        boolean password = codigos
                .findFirstByUsuarioIdAndPropositoAndConsumidoFalseAndExpiraEnAfterOrderByCreadoEnDesc(
                        u.getId(), PropositoCodigo.CAMBIO_PASSWORD, ahora).isPresent();
        Optional<CodigoSeguridad> cambioEmail = codigos
                .findFirstByUsuarioIdAndPropositoAndConsumidoFalseAndExpiraEnAfterOrderByCreadoEnDesc(
                        u.getId(), PropositoCodigo.CAMBIO_EMAIL, ahora);
        return new CredencialesView(
                CorreoService.enmascarar(u.getEmail()),
                password,
                cambioEmail.isPresent(),
                cambioEmail.map(CodigoSeguridad::getNuevoEmail).orElse(""),
                vigenciaMinutos);
    }

    // ------------------------------------------------------------------
    // Contraseña
    // ------------------------------------------------------------------
    /** Envía un código al correo registrado. Si el envío falla, no queda ningún código pendiente. */
    @Transactional
    public void solicitarCodigoPassword(String email) {
        Usuario u = usuarioDe(email);
        emitirCodigo(u, PropositoCodigo.CAMBIO_PASSWORD, null,
                "Código para cambiar tu contraseña",
                "Recibimos una solicitud para <b>cambiar la contraseña</b> de tu cuenta médica en MediCerca.");
    }

    /** Verifica el código y, solo si es correcto, guarda la nueva contraseña (BCrypt). */
    @Transactional(noRollbackFor = NegocioException.class)
    public void cambiarPassword(String email, String codigo, String nueva, String confirmacion) {
        Usuario u = usuarioDe(email);

        // Validaciones que NO dependen del código: primero, para no gastar intentos por un error de formulario.
        registro.validarContrasena(nueva);
        if (!nueva.equals(confirmacion)) {
            throw new NegocioException("Las contraseñas no coinciden.");
        }
        if (encoder.matches(nueva, u.getPasswordHash())) {
            throw new NegocioException("La nueva contraseña debe ser distinta de la actual.");
        }

        CodigoSeguridad c = verificar(u, PropositoCodigo.CAMBIO_PASSWORD, codigo);

        u.setPasswordHash(encoder.encode(nueva));
        usuarios.save(u);
        c.setConsumido(true);

        String destino = u.getEmail();
        despuesDeCommit(() -> correo.enviarHtml(destino, "Tu contraseña de MediCerca fue cambiada",
                aviso("Tu contraseña fue cambiada",
                        "La contraseña de tu cuenta médica en MediCerca se cambió correctamente. "
                                + "Si no fuiste tú, comunícate con el administrador de inmediato.")));
    }

    // ------------------------------------------------------------------
    // Correo de inicio de sesión
    // ------------------------------------------------------------------
    /**
     * Valida el correo nuevo (formato, distinto del actual y no registrado por otra cuenta), lo deja ligado al
     * código y envía el código de identidad al correo ACTUAL registrado.
     */
    @Transactional
    public void solicitarCodigoEmail(String email, String nuevoEmail, String confirmacion) {
        Usuario u = usuarioDe(email);
        String nuevo = validarNuevoEmail(u, nuevoEmail);
        String repetido = confirmacion == null ? "" : confirmacion.trim().toLowerCase();
        if (!nuevo.equals(repetido)) {
            throw new NegocioException("Los correos no coinciden.");
        }
        emitirCodigo(u, PropositoCodigo.CAMBIO_EMAIL, nuevo,
                "Código para cambiar tu correo de acceso",
                "Recibimos una solicitud para cambiar el correo de inicio de sesión de tu cuenta médica en MediCerca "
                        + "por <b>" + NotificacionMedicoService.Html.esc(nuevo) + "</b>.");
    }

    /**
     * Verifica el código y cambia el correo al que quedó ligado al pedirlo (no a uno enviado ahora por el formulario).
     *
     * @return el correo nuevo ya guardado
     */
    @Transactional(noRollbackFor = NegocioException.class)
    public String cambiarEmail(String email, String codigo) {
        Usuario u = usuarioDe(email);
        CodigoSeguridad c = verificar(u, PropositoCodigo.CAMBIO_EMAIL, codigo);

        String nuevo = c.getNuevoEmail();
        c.setConsumido(true); // de un solo uso, salga bien o mal lo que sigue
        if (nuevo == null || nuevo.isBlank()) {
            throw new NegocioException("La solicitud de cambio de correo no es válida. Pide un código nuevo.");
        }
        // Se revalida: otra cuenta pudo registrar ese correo mientras el código estaba pendiente.
        if (usuarios.existsByEmail(nuevo)) {
            throw new NegocioException("Ese correo ya está registrado por otra cuenta. Elige otro y pide un código nuevo.");
        }

        String anterior = u.getEmail();
        u.setEmail(nuevo);
        // flush: si otra cuenta lo tomó en este mismo instante, la restricción UNIQUE falla aquí (y se revierte todo).
        usuarios.saveAndFlush(u);

        despuesDeCommit(() -> {
            correo.enviarHtml(anterior, "El correo de acceso de tu cuenta MediCerca fue cambiado",
                    aviso("Cambiaron tu correo de acceso",
                            "El correo de inicio de sesión de tu cuenta médica pasó a "
                                    + NotificacionMedicoService.Html.esc(CorreoService.enmascarar(nuevo)) + ". "
                                    + "Si no fuiste tú, comunícate con el administrador de inmediato."));
        });
        despuesDeCommit(() -> correo.enviarHtml(nuevo, "Ya puedes usar este correo en MediCerca",
                aviso("Correo de acceso actualizado",
                        "Desde ahora inicias sesión en MediCerca con este correo y tu contraseña de siempre.")));
        return nuevo;
    }

    // ------------------------------------------------------------------
    // Núcleo: emisión y verificación de códigos
    // ------------------------------------------------------------------
    private void emitirCodigo(Usuario u, PropositoCodigo proposito, String nuevoEmail, String asunto, String motivoHtml) {
        LocalDateTime ahora = LocalDateTime.now();

        // Limpieza de códigos viejos de este usuario (vencidos hace más de un día).
        codigos.deleteByUsuarioIdAndExpiraEnBefore(u.getId(), ahora.minusDays(1));

        // Bloquea los códigos pendientes de este propósito: dos peticiones a la vez no se pisan.
        List<CodigoSeguridad> pendientes = codigos.findByUsuarioIdAndPropositoAndConsumidoFalse(u.getId(), proposito);

        Optional<CodigoSeguridad> ultimo = codigos.findFirstByUsuarioIdAndPropositoOrderByCreadoEnDesc(u.getId(), proposito);
        if (ultimo.isPresent() && ultimo.get().getCreadoEn().plusSeconds(segundosEntreEnvios).isAfter(ahora)) {
            long faltan = Duration.between(ahora, ultimo.get().getCreadoEn().plusSeconds(segundosEntreEnvios))
                    .toSeconds() + 1;
            throw new NegocioException("Espera " + faltan + " segundos antes de pedir otro código.");
        }
        if (codigos.countByUsuarioIdAndCreadoEnAfter(u.getId(), ahora.minusHours(1)) >= maxPorHora) {
            throw new NegocioException("Pediste demasiados códigos. Inténtalo de nuevo en una hora.");
        }

        // Pedir un código nuevo anula el anterior que siguiera pendiente.
        pendientes.forEach(p -> p.setConsumido(true));

        String codigo = String.format(Locale.ROOT, "%06d", ALEATORIO.nextInt(1_000_000));
        CodigoSeguridad c = new CodigoSeguridad();
        c.setUsuario(u);
        c.setProposito(proposito);
        c.setCodigoHash(encoder.encode(codigo));
        c.setNuevoEmail(nuevoEmail);
        c.setIntentos(0);
        c.setConsumido(false);
        c.setCreadoEn(ahora);
        c.setExpiraEn(ahora.plusMinutes(vigenciaMinutos));
        codigos.save(c);

        // Envío REAL dentro de la transacción: si falla (NegocioException) se revierte y no queda un código huérfano.
        correo.enviarHtml(u.getEmail(), asunto + " — MediCerca", htmlCodigo(motivoHtml, codigo));
        log.info("Código de seguridad ({}) enviado a {}", proposito, CorreoService.enmascarar(u.getEmail()));
    }

    /**
     * Comprueba el código vigente del usuario. Cuenta los intentos fallidos (se guardan gracias a noRollbackFor
     * en quien llama) y anula el código al agotar los intentos o al caducar. NO lo marca como usado: lo hace
     * quien llama, en la misma transacción que aplica el cambio.
     */
    private CodigoSeguridad verificar(Usuario u, PropositoCodigo proposito, String ingresado) {
        List<CodigoSeguridad> activos = codigos.findByUsuarioIdAndPropositoAndConsumidoFalse(u.getId(), proposito);
        CodigoSeguridad c = activos.stream().max(Comparator.comparing(CodigoSeguridad::getCreadoEn))
                .orElseThrow(() -> new NegocioException("Primero solicita un código de seguridad."));

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

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------
    /** El usuario dueño de la sesión; exige rol MEDICO y cuenta ACTIVA (si no, 403). */
    private Usuario usuarioDe(String email) {
        return medicoSesion.actual(email).getUsuario();
    }

    private String validarNuevoEmail(Usuario u, String nuevoEmail) {
        String nuevo = registro.validarEmail(nuevoEmail);
        if (nuevo.equalsIgnoreCase(u.getEmail())) {
            throw new NegocioException("El nuevo correo es igual al que ya usas.");
        }
        if (usuarios.existsByEmail(nuevo)) {
            throw new NegocioException("Ese correo ya está registrado por otra cuenta.");
        }
        return nuevo;
    }

    /** Los avisos posteriores al cambio se envían cuando la base ya confirmó; si fallan, solo se registra. */
    private void despuesDeCommit(Runnable envio) {
        Runnable seguro = () -> {
            try {
                envio.run();
            } catch (RuntimeException e) {
                log.warn("El cambio se guardó, pero no se pudo enviar el aviso por correo: {}", e.getMessage());
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            seguro.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                seguro.run();
            }
        });
    }

    private String htmlCodigo(String motivoHtml, String codigo) {
        return marco("Tu código de seguridad",
                "<p>" + motivoHtml + "</p>"
                        + "<p style=\"font-size:32px;letter-spacing:8px;font-weight:700;color:#0F766E;margin:18px 0\">"
                        + codigo + "</p>"
                        + "<p>El código vence en <b>" + vigenciaMinutos + " minutos</b> y solo se puede usar una vez. "
                        + "No lo compartas con nadie.</p>"
                        + "<p style=\"color:#6b7280;font-size:13px\">Si no fuiste tú, ignora este mensaje: "
                        + "tu cuenta sigue segura y nada cambiará.</p>");
    }

    private String aviso(String titulo, String texto) {
        return marco(titulo, "<p>" + texto + "</p>");
    }

    private String marco(String titulo, String cuerpo) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;color:#1f2937;"
                + "line-height:1.55\"><h2 style=\"color:#0F766E\">" + titulo + "</h2>" + cuerpo
                + "<hr style=\"border:none;border-top:1px solid #e5e7eb;margin:24px 0\">"
                + "<p style=\"font-size:12px;color:#6b7280\">Mensaje automático de MediCerca.</p></div>";
    }
}
