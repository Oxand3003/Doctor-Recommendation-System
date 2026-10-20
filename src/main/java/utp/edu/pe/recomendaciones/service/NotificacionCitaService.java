package utp.edu.pe.recomendaciones.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import utp.edu.pe.recomendaciones.dto.CitaCancelada;
import utp.edu.pe.recomendaciones.dto.ReservaCreada;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.service.NotificacionMedicoService.Html;

/**
 * Correos al paciente sobre sus citas: reserva registrada y reserva eliminada por el médico.
 * Reutiliza CorreoService (misma configuración SMTP). Se invoca DESPUÉS de que el cambio quedó guardado:
 * si el correo falla, la reserva o la eliminación siguen vigentes y solo se devuelve false (y se registra).
 */
@Service
public class NotificacionCitaService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionCitaService.class);

    private final CorreoService correo;
    private final String urlBase;

    public NotificacionCitaService(CorreoService correo,
                                   @Value("${medicerca.app.base-url:http://localhost:8080}") String urlBase) {
        this.correo = correo;
        this.urlBase = urlBase == null ? "" : urlBase.trim().replaceAll("/+$", "");
    }

    /** @return true si el correo salió; false si no se pudo enviar (la reserva ya está guardada). */
    public boolean reservaCreada(String emailPaciente, String nombrePaciente, ReservaCreada r) {
        return enviar(emailPaciente, "Tu cita en MediCerca fue reservada (" + r.codigo() + ")",
                htmlReserva(nombrePaciente, r));
    }

    /** @return true si el correo salió; false si no se pudo enviar (la cita ya está eliminada). */
    public boolean citaEliminada(CitaCancelada c) {
        return enviar(c.pacienteEmail(), "Tu cita en MediCerca fue cancelada (" + c.codigo() + ")", htmlEliminada(c));
    }

    private boolean enviar(String destino, String asunto, String html) {
        try {
            correo.enviarHtml(destino, asunto, html);
            return true;
        } catch (NegocioException e) {
            log.warn("El cambio en la cita se guardó, pero no se pudo notificar a {}.", CorreoService.enmascarar(destino));
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Contenido de los correos
    // ------------------------------------------------------------------
    String htmlReserva(String nombre, ReservaCreada r) {
        return plantilla("¡Tu reserva fue registrada!",
                "<p>Hola " + Html.esc(nombre) + ",</p>"
                        + "<p>Tu cita con <b>" + Html.esc(r.medico()) + "</b> quedó <b>reservada correctamente</b>.</p>"
                        + tabla(r.codigo(), r.fecha(), r.hora(), r.modalidad())
                        + "<p>El médico se pondrá en contacto contigo por WhatsApp, al número que registraste, "
                        + "para coordinar el pago. Mientras tanto, el horario ya es tuyo.</p>"
                        + "<p><a href=\"" + Html.esc(urlBase + "/account#my-appointments") + "\" style=\"" + BOTON
                        + "\">Ver mis citas</a></p>");
    }

    String htmlEliminada(CitaCancelada c) {
        String motivo = c.motivo() == null || c.motivo().isBlank() ? ""
                : "<p><b>Motivo indicado por el médico:</b><br>" + Html.esc(c.motivo()).replace("\n", "<br>") + "</p>";
        return plantilla("Tu cita fue cancelada",
                "<p>Hola " + Html.esc(c.pacienteNombre()) + ",</p>"
                        + "<p>Lamentamos avisarte que <b>" + Html.esc(c.medico())
                        + "</b> eliminó tu reserva. El horario quedó libre.</p>"
                        + tabla(c.codigo(), c.fecha(), c.hora(), c.modalidad()) + motivo
                        + "<p>Puedes buscar otro horario o a otro médico cuando quieras.</p>"
                        + "<p><a href=\"" + Html.esc(urlBase + "/") + "\" style=\"" + BOTON + "\">Buscar médicos</a></p>");
    }

    private static final String BOTON = "display:inline-block;background:#0F766E;color:#fff;padding:10px 20px;"
            + "border-radius:8px;text-decoration:none;font-weight:600";

    private static String tabla(String codigo, String fecha, String hora, String modalidad) {
        return "<table style=\"border-collapse:collapse;margin:12px 0\">"
                + fila("Código", codigo) + fila("Fecha", fecha) + fila("Hora", hora) + fila("Modalidad", modalidad)
                + "</table>";
    }

    private static String fila(String k, String v) {
        return "<tr><td style=\"padding:4px 16px 4px 0;color:#6b7280\">" + k + "</td><td style=\"padding:4px 0\"><b>"
                + Html.esc(v) + "</b></td></tr>";
    }

    private String plantilla(String titulo, String cuerpo) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:560px;margin:0 auto;color:#1f2937;"
                + "line-height:1.55\"><h2 style=\"color:#0F766E\">" + titulo + "</h2>" + cuerpo
                + "<hr style=\"border:none;border-top:1px solid #e5e7eb;margin:24px 0\">"
                + "<p style=\"font-size:12px;color:#6b7280\">Este mensaje se envió automáticamente desde MediCerca.</p></div>";
    }
}
