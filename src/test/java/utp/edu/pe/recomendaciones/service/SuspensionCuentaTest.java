package utp.edu.pe.recomendaciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
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
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;

/** Suspender una cuenta exige un motivo, se le envía por correo al usuario y se borra al reactivarla. */
class SuspensionCuentaTest extends BaseMedicosTest {

    @Autowired AdminApiController api;

    private final Authentication auth = new UsernamePasswordAuthenticationToken(ADMIN, null);

    private Long medicoId(String email) {
        Usuario u = usuarios.findByEmail(email).orElseThrow();
        return medicos.findByUsuarioId(u.getId()).orElseThrow().getId();
    }

    @Test
    void suspenderUnMedicoGuardaElMotivoYSeLoEnviaPorCorreo() {
        String email = medicoActivo();
        clearInvocations(correo);

        Map<String, Object> r = api.suspender(medicoId(email), "Varias denuncias por cancelar <citas> sin aviso", auth);

        assertThat(r).containsEntry("ok", true).doesNotContainKey("warning");
        Usuario u = usuarios.findByEmail(email).orElseThrow();
        assertThat(u.getEstado()).isEqualTo(EstadoUsuario.SUSPENDIDO);
        assertThat(u.getMotivoSuspension()).isEqualTo("Varias denuncias por cancelar <citas> sin aviso");
        ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(correo, times(1)).enviarHtml(eq(email), asunto.capture(), html.capture());
        assertThat(asunto.getValue()).containsIgnoringCase("suspendida");
        assertThat(html.getValue()).contains("Varias denuncias por cancelar &lt;citas&gt; sin aviso")
                .doesNotContain("Si no solicitaste registrarte");
        // El panel lo muestra junto a la cuenta.
        assertThat(admin.datos().doctors()).filteredOn(d -> d.email().equals(email))
                .singleElement().extracting("motivoSuspension")
                .isEqualTo("Varias denuncias por cancelar <citas> sin aviso");
    }

    @Test
    void sinMotivoNoSeSuspendeNiSeEnviaCorreo() {
        String email = medicoActivo();
        clearInvocations(correo);

        assertThatThrownBy(() -> api.suspender(medicoId(email), "  corto ", auth))
                .isInstanceOf(NegocioException.class).hasMessageContaining("motivo");

        assertThat(usuarios.findByEmail(email).orElseThrow().getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
        verify(correo, never()).enviarHtml(eq(email), anyString(), anyString());
    }

    @Test
    void alReactivarSeBorraElMotivo() {
        String email = medicoActivo();
        Long id = medicoId(email);
        api.suspender(id, "Documentos vencidos, debe actualizarlos", auth);

        api.reactivar(id, auth);

        Usuario u = usuarios.findByEmail(email).orElseThrow();
        assertThat(u.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(u.getMotivoSuspension()).isNull();
    }

    @Test
    void siElCorreoFallaLaSuspensionSeGuardaYElAdminRecibeUnAviso() {
        String email = medicoActivo();
        doThrow(new NegocioException("El envío de correos no está disponible en este momento."))
                .when(correo).enviarHtml(anyString(), anyString(), anyString());

        Map<String, Object> r = api.suspender(medicoId(email), "Suplantación de identidad reportada", auth);

        assertThat(r).containsEntry("ok", true);
        assertThat(String.valueOf(r.get("warning"))).contains("no se pudo enviar el correo con el motivo");
        assertThat(usuarios.findByEmail(email).orElseThrow().getEstado()).isEqualTo(EstadoUsuario.SUSPENDIDO);
    }
}
