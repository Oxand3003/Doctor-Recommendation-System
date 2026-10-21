package utp.edu.pe.recomendaciones.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import utp.edu.pe.recomendaciones.dto.DecisionMedico;
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

    // ------------------------------------------------------------------
    // Contenido de los correos
    // ------------------------------------------------------------------
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
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;color:#1f2937;"
                + "line-height:1.55\"><h2 style=\"color:#0F766E\">" + titulo + "</h2>" + cuerpo
                + "<hr style=\"border:none;border-top:1px solid #e5e7eb;margin:24px 0\">"
                + "<p style=\"font-size:12px;color:#6b7280\">Este mensaje se envió automáticamente desde MediCerca. "
                + "Si no solicitaste registrarte, puedes ignorarlo.</p></div>";
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
