package utp.edu.pe.recomendaciones.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import utp.edu.pe.recomendaciones.dto.DecisionMedico;
import utp.edu.pe.recomendaciones.dto.SuspensionCuenta;
import utp.edu.pe.recomendaciones.exception.NegocioException;

/**
 * Avisa por correo al médico que se registró cuando un administrador aprueba o rechaza su solicitud.
 * Reutiliza CorreoService (misma configuración SMTP de la parte 1). Se invoca DESPUÉS de que la decisión
 * quedó guardada: si el correo falla, la decisión sigue vigente y el administrador recibe un aviso.
 */
@Service
public class NotificacionMedicoService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionMedicoService.class);

    private final CorreoService correo;
    private final String urlBase;

    public NotificacionMedicoService(CorreoService correo,
                                     @Value("${medicerca.app.base-url:http://localhost:8080}") String urlBase) {
        this.correo = correo;
        this.urlBase = urlBase == null ? "" : urlBase.trim().replaceAll("/+$", "");
    }

    /**
     * Envía el correo que corresponde a la decisión.
     *
     * @return null si el correo salió bien; si no, un mensaje para mostrar al administrador.
     */
    public String notificar(DecisionMedico d) {
        String destino = d.email();
        try {
            if (d.aprobada()) {
                correo.enviarHtml(destino, "Tu cuenta médica en MediCerca fue aprobada", htmlAprobacion(d));
            } else {
                correo.enviarHtml(destino, "Resultado de tu solicitud en MediCerca", htmlRechazo(d));
            }
            return null;
        } catch (NegocioException e) {
            // CorreoService ya registró el detalle técnico (sin secretos); aquí solo se deja constancia de la consecuencia.
            log.warn("La decisión sobre {} se guardó, pero no se pudo notificar por correo.",
                    CorreoService.enmascarar(destino));
            return "La decisión se guardó, pero no se pudo enviar el correo al médico (" + e.getMessage()
                    + ") Avísale por otro medio.";
        }
    }

    /**
     * Avisa al médico o paciente que su cuenta fue suspendida y por qué. Se invoca después de guardar la
     * suspensión: si el correo falla, la cuenta sigue suspendida y el administrador recibe el aviso devuelto.
     *
     * @return null si el correo salió bien; si no, un mensaje para mostrar al administrador.
     */
    public String notificarSuspension(SuspensionCuenta s) {
        try {
            correo.enviarHtml(s.email(), "Tu cuenta de MediCerca fue suspendida", htmlSuspension(s));
            return null;
        } catch (NegocioException e) {
            log.warn("La suspensión de {} se guardó, pero no se pudo notificar por correo.",
                    CorreoService.enmascarar(s.email()));
            return "La cuenta quedó suspendida, pero no se pudo enviar el correo con el motivo (" + e.getMessage()
                    + ") Avísale por otro medio.";
        }
    }

    // ------------------------------------------------------------------
    // Contenido de los correos
    // ------------------------------------------------------------------
    String htmlSuspension(SuspensionCuenta s) {
        String contacto = urlBase + "/#contacto";
        return plantilla("Tu cuenta fue suspendida",
                "<p>Hola " + Html.esc(s.nombre()) + ",</p>"
                        + "<p>Un administrador de MediCerca <b>suspendió tu cuenta</b>. Mientras esté suspendida no "
                        + "podrás iniciar sesión.</p>"
                        + "<p><b>Motivo indicado por el administrador:</b><br>"
                        + Html.esc(s.motivo()).replace("\n", "<br>") + "</p>"
                        + "<p>Si crees que se trata de un error o quieres resolverlo, escríbenos desde el formulario "
                        + "de contacto eligiendo «Problemas con mi cuenta o mi acceso».</p>"
                        + "<p><a href=\"" + Html.esc(contacto) + "\" style=\"display:inline-block;background:#0F766E;"
                        + "color:#fff;padding:10px 20px;border-radius:8px;text-decoration:none;font-weight:600\">"
                        + "Escribir a MediCerca</a></p>",
                "Este mensaje se envió automáticamente desde MediCerca porque tu cuenta cambió de estado.");
    }

    String htmlAprobacion(DecisionMedico d) {
        String login = urlBase + "/login";
        return plantilla("¡Bienvenido a MediCerca, " + Html.esc(d.nombre()) + "!",
                "<p>Un administrador revisó y <b>aprobó tu solicitud de registro médico</b>. "
                        + "Tu cuenta ya está activa y puedes iniciar sesión con el correo y la contraseña que registraste.</p>"
                        + "<p>Desde tu perfil podrás completar tu información profesional, tus horarios, tus sedes "
                        + "y tus tarifas para que los pacientes te encuentren.</p>"
                        + "<p><a href=\"" + Html.esc(login) + "\" style=\"display:inline-block;background:#0F766E;"
                        + "color:#fff;padding:10px 20px;border-radius:8px;text-decoration:none;font-weight:600\">"
                        + "Iniciar sesión</a></p>");
    }

    String htmlRechazo(DecisionMedico d) {
        return plantilla("Tu solicitud no fue aprobada",
                "<p>Hola " + Html.esc(d.nombre()) + ",</p>"
                        + "<p>Revisamos tu solicitud de registro como médico en MediCerca y, lamentablemente, "
                        + "<b>no pudo ser aprobada</b>.</p>"
                        + "<p><b>Motivo indicado por el administrador:</b><br>"
                        + Html.esc(d.motivo()).replace("\n", "<br>") + "</p>"
                        + "<p>Puedes volver a enviar tu solicitud desde “Crear cuenta &gt; Soy médico”, "
                        + "usando el mismo correo y la misma contraseña, una vez corregido lo indicado.</p>");
    }

    private String plantilla(String titulo, String cuerpo) {
        return plantilla(titulo, cuerpo, "Este mensaje se envió automáticamente desde MediCerca. "
                + "Si no solicitaste registrarte, puedes ignorarlo.");
    }

    private String plantilla(String titulo, String cuerpo, String pie) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;color:#1f2937;"
                + "line-height:1.55\"><h2 style=\"color:#0F766E\">" + titulo + "</h2>" + cuerpo
                + "<hr style=\"border:none;border-top:1px solid #e5e7eb;margin:24px 0\">"
                + "<p style=\"font-size:12px;color:#6b7280\">" + pie + "</p></div>";
    }

    /** Escapado mínimo para texto que se inserta dentro de HTML (nombres y motivos escritos por personas). */
    static final class Html {
        private Html() { }

        static String esc(String s) {
            if (s == null) return "";
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\"", "&quot;").replace("'", "&#39;");
        }
    }
}
