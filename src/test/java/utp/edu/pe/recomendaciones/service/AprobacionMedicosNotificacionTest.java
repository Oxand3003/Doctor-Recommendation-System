package utp.edu.pe.recomendaciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import utp.edu.pe.recomendaciones.Controller.AdminApiController;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.enums.EstadoSolicitud;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;

/** Aprobar / rechazar solicitudes de médicos: correo al solicitante, sin duplicados y tolerando fallos de envío. */
class AprobacionMedicosNotificacionTest extends BaseMedicosTest {

    @Autowired AdminApiController api;

    /** Autenticación mínima del administrador (el controlador solo usa su nombre = correo). */
    private final Authentication auth = new UsernamePasswordAuthenticationToken(ADMIN, null);

    @Test
    void aprobarEnviaUnCorreoDeBienvenidaALaDireccionRegistrada() {
        String email = registrarPendiente();
        Long id = solicitudDe(email);

        Map<String, Object> r = api.aprobar(id, auth);

        assertThat(r).containsEntry("ok", true).doesNotContainKey("warning");
        assertThat(usuarios.findByEmail(email).orElseThrow().getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(correo, times(1)).enviarHtml(eq(email), asunto.capture(), html.capture());
        assertThat(asunto.getValue()).containsIgnoringCase("aprobada");
        assertThat(html.getValue()).contains("aprobó").contains("/login");
    }

    @Test
    void aprobarDosVecesNoDuplicaElCorreo() {
        String email = registrarPendiente();
        Long id = solicitudDe(email);
        api.aprobar(id, auth);

        assertThatThrownBy(() -> api.aprobar(id, auth))
                .isInstanceOf(NegocioException.class).hasMessageContaining("ya fue revisada");

        verify(correo, times(1)).enviarHtml(eq(email), anyString(), anyString());
    }

    @Test
    void rechazarEnviaElMotivoYDejaLaCuentaPendiente() {
        String email = registrarPendiente();
        Long id = solicitudDe(email);

        Map<String, Object> r = api.rechazar(id, "El CMP no coincide <con> el registro", auth);

        assertThat(r).containsEntry("ok", true).doesNotContainKey("warning");
        assertThat(solicitudes.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoSolicitud.RECHAZADA);
        assertThat(usuarios.findByEmail(email).orElseThrow().getEstado()).isEqualTo(EstadoUsuario.PENDIENTE);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(correo, times(1)).enviarHtml(eq(email), anyString(), html.capture());
        assertThat(html.getValue()).contains("no pudo ser aprobada")
                .contains("El CMP no coincide &lt;con&gt; el registro"); // el motivo se escapa en el HTML
    }

    @Test
    void rechazarDosVecesNoDuplicaElCorreo() {
        String email = registrarPendiente();
        Long id = solicitudDe(email);
        api.rechazar(id, "Documento ilegible", auth);

        assertThatThrownBy(() -> api.rechazar(id, "Otro motivo", auth)).isInstanceOf(NegocioException.class);

        verify(correo, times(1)).enviarHtml(eq(email), anyString(), anyString());
    }

    @Test
    void rechazoSinMotivoNoCambiaNadaNiEnviaCorreo() {
        String email = registrarPendiente();
        Long id = solicitudDe(email);

        assertThatThrownBy(() -> api.rechazar(id, "  ", auth)).isInstanceOf(NegocioException.class);

        assertThat(solicitudes.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        verify(correo, never()).enviarHtml(eq(email), anyString(), anyString());
    }

    @Test
    void siElCorreoFallaLaDecisionSeGuardaYElAdminRecibeUnAviso() {
        doThrow(new NegocioException("El envío de correos no está disponible en este momento."))
                .when(correo).enviarHtml(anyString(), anyString(), anyString());
        String email = registrarPendiente();
        Long id = solicitudDe(email);

        Map<String, Object> r = api.aprobar(id, auth);

        assertThat(r).containsEntry("ok", true).containsKey("warning");
        assertThat(String.valueOf(r.get("warning"))).contains("no se pudo enviar el correo");
        assertThat(usuarios.findByEmail(email).orElseThrow().getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(solicitudes.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoSolicitud.APROBADA);
    }
}
