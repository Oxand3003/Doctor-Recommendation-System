package utp.edu.pe.recomendaciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import utp.edu.pe.recomendaciones.dto.DecisionMedico;
import utp.edu.pe.recomendaciones.exception.NegocioException;

/** Contenido de los correos de aprobación/rechazo. Sin Spring ni red. */
class NotificacionMedicoServiceTest {

    private final CorreoService correo = mock(CorreoService.class);
    private final NotificacionMedicoService servicio = new NotificacionMedicoService(correo, "https://medicerca.pe/");

    @Test
    void aprobacionLlevaEnlaceDeLoginSinBarraDoble() {
        String aviso = servicio.notificar(new DecisionMedico(true, "ana@x.com", "Ana <Torres>", null));

        assertThat(aviso).isNull();
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(correo).enviarHtml(eq("ana@x.com"), anyString(), html.capture());
        assertThat(html.getValue()).contains("https://medicerca.pe/login").doesNotContain("//login")
                .contains("Ana &lt;Torres&gt;");
    }

    @Test
    void rechazoIncluyeElMotivoYLaOpcionDeReenviar() {
        servicio.notificar(new DecisionMedico(false, "ana@x.com", "Ana Torres", "Falta el sustento\nlegible"));

        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(correo).enviarHtml(eq("ana@x.com"), anyString(), html.capture());
        assertThat(html.getValue()).contains("no pudo ser aprobada").contains("Falta el sustento<br>legible")
                .contains("volver a enviar");
    }

    @Test
    void siElEnvioFallaDevuelveUnAvisoYNoLanzaExcepcion() {
        doThrow(new NegocioException("No se pudo enviar el correo.")).when(correo).enviarHtml(anyString(), anyString(), anyString());

        String aviso = servicio.notificar(new DecisionMedico(true, "ana@x.com", "Ana Torres", null));

        assertThat(aviso).contains("no se pudo enviar el correo");
    }
}
