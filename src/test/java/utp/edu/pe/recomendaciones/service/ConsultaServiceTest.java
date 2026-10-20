package utp.edu.pe.recomendaciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import utp.edu.pe.recomendaciones.dto.ConsultaForm;
import utp.edu.pe.recomendaciones.dto.ConsultaView;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Consulta;
import utp.edu.pe.recomendaciones.model.enums.CanalAtencion;
import utp.edu.pe.recomendaciones.model.enums.EstadoConsulta;
import utp.edu.pe.recomendaciones.model.enums.MotivoConsulta;
import utp.edu.pe.recomendaciones.repository.ConsultaRepository;

/** Formulario de contacto del inicio: validación y registro en la tabla consultas. */
class ConsultaServiceTest extends BaseMedicosTest {

    @Autowired ConsultaService consultas;
    @Autowired ConsultaRepository consultaRepo;

    private String correoUnico() {
        return "visitante" + ThreadLocalRandom.current().nextInt(1_000_000) + "@test.local";
    }

    @Test
    void guardaLaConsultaPendienteConSusDatos() {
        String correo = correoUnico();
        Consulta c = consultas.registrar(new ConsultaForm("  Ana   Torres ", correo.toUpperCase(), "987 654 321",
                "ELIMINAR_CUENTA", "Quiero que eliminen mi cuenta por favor."), null);

        Consulta guardada = consultaRepo.findById(c.getId()).orElseThrow();
        assertThat(guardada.getNombres()).isEqualTo("Ana Torres");
        assertThat(guardada.getCorreo()).isEqualTo(correo);
        assertThat(guardada.getTelefono()).isEqualTo("987 654 321");
        assertThat(guardada.getMotivo()).isEqualTo(MotivoConsulta.ELIMINAR_CUENTA);
        assertThat(guardada.getEstado()).isEqualTo(EstadoConsulta.PENDIENTE);
        assertThat(guardada.getFechaCreacion()).isNotNull();
        assertThat(guardada.getUsuario()).isNull();
    }

    @Test
    void rechazaDatosInvalidos() {
        String ok = "Mensaje con suficiente detalle.";
        assertThatThrownBy(() -> consultas.registrar(new ConsultaForm("", correoUnico(), "987654321", "OTRO", ok), null))
                .isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> consultas.registrar(new ConsultaForm("Ana Torres", "no-es-correo", "987654321", "OTRO", ok), null))
                .isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> consultas.registrar(new ConsultaForm("Ana Torres", correoUnico(), "abc", "OTRO", ok), null))
                .isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> consultas.registrar(new ConsultaForm("Ana Torres", correoUnico(), "987654321", "INVENTADO", ok), null))
                .isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> consultas.registrar(new ConsultaForm("Ana Torres", correoUnico(), "987654321", "OTRO", "corto"), null))
                .isInstanceOf(NegocioException.class);
    }

    @Test
    void limitaElEnvioMasivoDesdeUnMismoCorreo() {
        String correo = correoUnico();
        for (int i = 0; i < 5; i++) {
            consultas.registrar(new ConsultaForm("Ana Torres", correo, "987654321", "SUGERENCIA", "Mensaje número " + i + " de prueba."), null);
        }
        assertThatThrownBy(() -> consultas.registrar(
                new ConsultaForm("Ana Torres", correo, "987654321", "SUGERENCIA", "Un mensaje más de prueba."), null))
                .isInstanceOf(NegocioException.class);
    }

    // ------------------------------------------------------------------
    // Panel de administración
    // ------------------------------------------------------------------
    private Consulta nueva(String correo) {
        return consultas.registrar(new ConsultaForm("Ana Torres", correo, "987 654 321", "DENUNCIAR",
                "Quiero denunciar un contenido inapropiado."), null);
    }

    @Test
    void elListadoIncluyeElEnlaceDeWhatsappConCodigoDePais() {
        Consulta c = nueva(correoUnico());
        ConsultaView v = consultas.listar().stream().filter(x -> x.id().equals(c.getId())).findFirst().orElseThrow();
        assertThat(v.whatsappUrl()).startsWith("https://wa.me/51987654321?text=");
        assertThat(v.estado()).isEqualTo("PENDIENTE");
        assertThat(v.motivo()).isEqualTo("Denunciar a un médico o un contenido");
    }

    @Test
    void responderPorCorreoEnviaElMensajeYLaPasaAlHistorial() {
        String destino = correoUnico();
        Consulta c = nueva(destino);

        consultas.responderPorCorreo(c.getId(), "Respuesta a tu consulta", "Ya revisamos tu denuncia, gracias.", true, ADMIN);

        verify(correo).enviarTexto(eq(destino), eq("Respuesta a tu consulta"), contains("Ya revisamos tu denuncia"));
        Consulta g = consultaRepo.findById(c.getId()).orElseThrow();
        assertThat(g.getEstado()).isEqualTo(EstadoConsulta.ATENDIDA);
        assertThat(g.getCanalAtencion()).isEqualTo(CanalAtencion.CORREO);
        assertThat(g.getAtendidaPor()).isEqualTo(ADMIN);
        assertThat(g.getFechaAtencion()).isNotNull();
        assertThat(g.getRespuesta()).isEqualTo("Ya revisamos tu denuncia, gracias.");
    }

    @Test
    void responderSinArchivarDejaLaConsultaPendiente() {
        Consulta c = nueva(correoUnico());
        consultas.responderPorCorreo(c.getId(), "Hola", "Necesitamos un dato más para ayudarte.", false, ADMIN);
        assertThat(consultaRepo.findById(c.getId()).orElseThrow().getEstado()).isEqualTo(EstadoConsulta.PENDIENTE);
    }

    @Test
    void siElCorreoFallaLaConsultaNoCambia() {
        Consulta c = nueva(correoUnico());
        doThrow(new NegocioException("No se pudo enviar el correo.")).when(correo).enviarTexto(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> consultas.responderPorCorreo(c.getId(), "Hola", "Mensaje de prueba válido.", true, ADMIN))
                .isInstanceOf(NegocioException.class);

        Consulta g = consultaRepo.findById(c.getId()).orElseThrow();
        assertThat(g.getEstado()).isEqualTo(EstadoConsulta.PENDIENTE);
        assertThat(g.getRespuesta()).isNull();
    }

    @Test
    void noEnviaCorreoConAsuntoOMensajeVacios() {
        Consulta c = nueva(correoUnico());
        assertThatThrownBy(() -> consultas.responderPorCorreo(c.getId(), " ", "Mensaje de prueba válido.", true, ADMIN))
                .isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> consultas.responderPorCorreo(c.getId(), "Hola", "ok", true, ADMIN))
                .isInstanceOf(NegocioException.class);
        verify(correo, never()).enviarTexto(eq(c.getCorreo()), anyString(), anyString());
    }

    @Test
    void marcarAtendidaLaArchivaYSePuedeReabrir() {
        Consulta c = nueva(correoUnico());

        ConsultaView v = consultas.marcarAtendida(c.getId(), "WHATSAPP", ADMIN);
        assertThat(v.estado()).isEqualTo("ATENDIDA");
        assertThat(v.canal()).isEqualTo("WhatsApp");
        assertThatThrownBy(() -> consultas.marcarAtendida(c.getId(), "WHATSAPP", ADMIN))
                .isInstanceOf(NegocioException.class);

        ConsultaView r = consultas.reabrir(c.getId());
        assertThat(r.estado()).isEqualTo("PENDIENTE");
        assertThat(r.fechaAtencion()).isNull();
        assertThat(r.atendidaPor()).isNull();
    }

    @Test
    void unaConsultaInexistenteSeRechaza() {
        assertThatThrownBy(() -> consultas.marcarAtendida(-1L, "OTRO", ADMIN)).isInstanceOf(NegocioException.class);
    }
}
