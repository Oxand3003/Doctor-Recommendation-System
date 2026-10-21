package utp.edu.pe.recomendaciones.service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import utp.edu.pe.recomendaciones.exception.NegocioException;

/**
 * Infraestructura de envío de correo (SMTP). La usan NotificacionMedicoService (aprobación/rechazo de médicos) y
 * CredencialesMedicoService (códigos de seguridad). La configuración está en application.properties (spring.mail.*
 * y medicerca.mail.*) y las credenciales vienen de variables de entorno o de config/mail.local.properties.
 *
 * Reglas: la aplicación arranca aunque no haya credenciales (disponible() == false y enviar* falla con un
 * mensaje claro); nunca se registra la contraseña; el destinatario y el asunto se limpian de saltos de línea.
 */
@Service
public class CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);

    /** Resultado de probar la conexión SMTP (sin enviar ningún correo). */
    public record Verificacion(Estado estado, String detalle) {
        public enum Estado { NO_CONFIGURADO, CONEXION_OK, ERROR }

        public boolean ok() {
            return estado == Estado.CONEXION_OK;
        }
    }

    private final ObjectProvider<JavaMailSender> remitente;
    private final String from;
    private final String fromName;

    public CorreoService(ObjectProvider<JavaMailSender> remitente,
                         @Value("${medicerca.mail.from:}") String from,
                         @Value("${medicerca.mail.from-name:MediCerca}") String fromName) {
        this.remitente = remitente;
        this.from = from;
        this.fromName = fromName;
    }

    /** true si hay servidor, usuario y contraseña SMTP: es decir, si enviar() puede intentarse. */
    public boolean disponible() {
        return motivoNoDisponible() == null;
    }

    /** Resumen sin secretos para el log: servidor, puerto y usuario enmascarado. */
    public String descripcion() {
        if (!(remitente.getIfAvailable() instanceof JavaMailSenderImpl s)) return "sin servidor SMTP";
        return s.getHost() + ":" + s.getPort() + ", usuario " + enmascarar(s.getUsername());
    }

    /** Qué falta para poder enviar, o null si todo está listo. */
    public String motivoNoDisponible() {
        if (!(remitente.getIfAvailable() instanceof JavaMailSenderImpl s) || vacio(s.getHost())) {
            return "No hay servidor SMTP configurado (spring.mail.host).";
        }
        if (vacio(s.getUsername())) return "Falta el usuario de correo (variable MAIL_USERNAME).";
        if (vacio(s.getPassword())) return "Falta la contraseña de aplicación (variable MAIL_PASSWORD).";
        if (vacio(remitenteDireccion(s))) return "Falta la dirección del remitente (MAIL_FROM).";
        return null;
    }

    /**
     * Abre la conexión SMTP y se autentica, pero NO envía ningún correo. Sirve para comprobar que el
     * host, el puerto y la contraseña de aplicación son correctos.
     */
    public Verificacion verificarConexion() {
        String falta = motivoNoDisponible();
        if (falta != null) return new Verificacion(Verificacion.Estado.NO_CONFIGURADO, falta);
        JavaMailSenderImpl s = (JavaMailSenderImpl) remitente.getObject();
        try {
            s.testConnection();
            return new Verificacion(Verificacion.Estado.CONEXION_OK, "Conexión y autenticación SMTP correctas (" + descripcion() + ").");
        } catch (MessagingException e) {
            // Se registra el tipo y el mensaje del servidor; nunca la contraseña.
            return new Verificacion(Verificacion.Estado.ERROR, "No se pudo conectar o autenticar: " + e.getMessage());
        }
    }

    /** Envía un correo de texto plano. Lanza NegocioException si el correo no está configurado o falla el envío. */
    public void enviarTexto(String para, String asunto, String cuerpo) {
        enviar(para, asunto, cuerpo, false);
    }

    /** Envía un correo HTML. Lanza NegocioException si el correo no está configurado o falla el envío. */
    public void enviarHtml(String para, String asunto, String html) {
        enviar(para, asunto, html, true);
    }

    private void enviar(String para, String asunto, String cuerpo, boolean html) {
        String falta = motivoNoDisponible();
        if (falta != null) {
            log.warn("Correo no enviado: {}", falta);
            throw new NegocioException("El envío de correos no está disponible en este momento.");
        }
        JavaMailSender s = remitente.getObject();
        try {
            MimeMessage mensaje = s.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
            h.setFrom(remitenteDireccion((JavaMailSenderImpl) s), fromName);
            h.setTo(sinSaltos(para));
            h.setSubject(sinSaltos(asunto));
            h.setText(cuerpo == null ? "" : cuerpo, html);
            s.send(mensaje);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            log.error("No se pudo enviar el correo a {}: {}", enmascarar(para), e.getMessage());
            throw new NegocioException("No se pudo enviar el correo. Inténtalo de nuevo en unos minutos.");
        }
    }

    private String remitenteDireccion(JavaMailSenderImpl s) {
        return vacio(from) ? s.getUsername() : from.trim();
    }

    private static String sinSaltos(String s) {
        return s == null ? "" : s.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    /** ana.perez@gmail.com → a***@gmail.com */
    static String enmascarar(String correo) {
        if (vacio(correo)) return "(sin definir)";
        int at = correo.indexOf('@');
        return at <= 0 ? "***" : correo.charAt(0) + "***" + correo.substring(at);
    }
}
