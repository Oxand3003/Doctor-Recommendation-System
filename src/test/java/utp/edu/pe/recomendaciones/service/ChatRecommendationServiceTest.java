package utp.edu.pe.recomendaciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import utp.edu.pe.recomendaciones.dto.ChatRecommendationRequest;
import utp.edu.pe.recomendaciones.dto.ChatRecommendationResponse;
import utp.edu.pe.recomendaciones.dto.ChatRecommendationResponse.Option;
import utp.edu.pe.recomendaciones.model.Especialidad;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.repository.DistritoRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;

/**
 * El chat del asistente sin Spring ni base de datos: la IA (simulada) redacta la respuesta y elige la
 * especialidad; cuando no responde, las reglas propias siguen orientando. La base solo tiene médicos de
 * Cardiología y Dermatología, como pasa en producción cuando faltan especialidades.
 */
class ChatRecommendationServiceTest {

    private static final String AVISO = "Es solo una orientación para buscar médico, no un diagnóstico.";

    private final ObjectMapper mapper = new ObjectMapper();
    private final MedicoRepository medicos = mock(MedicoRepository.class);
    private final ClienteIa ia = mock(ClienteIa.class);
    private ChatRecommendationService chat;

    @BeforeEach
    void preparar() {
        when(medicos.findByUsuarioEstadoOrderByRatingPromedioDesc(EstadoUsuario.ACTIVO)).thenReturn(List.of(
                medico(1L, "Rosa", "Medina", "Cardiología", "80.00"),
                medico(2L, "Luis", "Paredes", "Dermatología", "60.00")));
        chat = new ChatRecommendationService(medicos, mock(DistritoRepository.class), mock(DoctorProfileService.class),
                mock(AsistenteMemoriaService.class), ia, "Eres un asistente de orientación médica.");
    }

    private Medico medico(Long id, String nombres, String apellidos, String especialidad, String precio) {
        Usuario usuario = new Usuario();
        usuario.setNombres(nombres);
        usuario.setApellidos(apellidos);
        Especialidad e = new Especialidad();
        e.setNombre(especialidad);
        Medico m = new Medico();
        m.setId(id);
        m.setUsuario(usuario);
        m.setEspecialidad(e);
        m.setCostoConsulta(new BigDecimal(precio));
        return m;
    }

    private void laIaResponde(String json) throws Exception {
        when(ia.disponible()).thenReturn(true);
        when(ia.consultar(anyString(), anyString())).thenReturn(mapper.readTree(json));
    }

    private ChatRecommendationResponse escribe(String mensaje) {
        return chat.recommend(new ChatRecommendationRequest(mensaje, null, null, null, null, true, List.of(), null, null));
    }

    @Test
    void laIaEligeLaEspecialidadYRedactaLaRespuesta() throws Exception {
        laIaResponde("{\"specialty\":\"Cardiología\",\"ideal_specialty\":null,\"alternatives\":[],"
                + "\"reply\":\"Siento que estés pasando por eso. Un cardiólogo puede revisar cómo late tu corazón.\"}");

        ChatRecommendationResponse r = escribe("siento raro el cuerpo cuando subo escaleras");

        assertThat(r.specialty()).isEqualTo("Cardiología");
        assertThat(r.stage()).isEqualTo("LIBRE");
        assertThat(r.doctors()).extracting("name").containsExactly("Dra. Rosa Medina");
        // Texto de la IA + aviso fijo + lo que sale de la base (médicos y pregunta por el día).
        assertThat(r.reply()).startsWith("Siento que estés pasando por eso. Un cardiólogo puede revisar cómo late tu corazón. " + AVISO)
                .contains("Estos son los médicos de Cardiología").contains("¿Para qué día");
    }

    @Test
    void laIaRecibeLosDatosRealesDeLaBaseYLaConversacion() throws Exception {
        laIaResponde("{\"specialty\":null,\"reply\":\"¡Hola!\"}");

        escribe("hola, ¿qué especialidades tienen?");

        ArgumentCaptor<String> instrucciones = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> conversacion = ArgumentCaptor.forClass(String.class);
        verify(ia).consultar(instrucciones.capture(), conversacion.capture());
        assertThat(instrucciones.getValue()).contains("[\"Cardiología\",\"Dermatología\"]")
                .contains("Cardiología: 1 médico, consulta desde S/ 80")
                .contains("Dermatología: 1 médico, consulta desde S/ 60");
        assertThat(conversacion.getValue()).endsWith("Paciente: hola, ¿qué especialidades tienen?");
    }

    @Test
    void conversaAunqueElPacienteNoDescribaSintomas() throws Exception {
        laIaResponde("{\"specialty\":null,\"ideal_specialty\":null,\"alternatives\":[],"
                + "\"reply\":\"¡Hola! Con gusto te ayudo. ¿Qué molestia tienes?\"}");

        ChatRecommendationResponse r = escribe("hola buenas noches");

        assertThat(r.reply()).isEqualTo("¡Hola! Con gusto te ayudo. ¿Qué molestia tienes?");
        assertThat(r.specialty()).isNull();
        assertThat(r.stage()).isEqualTo("MENU");
    }

    @Test
    void siLaIaIndicaUnaEspecialidadSinMedicosSeOfrecenLasDisponibles() throws Exception {
        laIaResponde("{\"specialty\":null,\"ideal_specialty\":\"Oftalmología\",\"alternatives\":[],"
                + "\"reply\":\"Eso lo ve un oftalmólogo, pero por ahora MediCerca no tiene médicos de esa especialidad.\"}");

        ChatRecommendationResponse r = escribe("veo doble desde ayer");

        assertThat(r.specialty()).isNull();
        assertThat(r.stage()).isEqualTo("ELEGIR");
        assertThat(r.reply()).startsWith("Eso lo ve un oftalmólogo").endsWith(AVISO);
        assertThat(r.options()).extracting(Option::label).contains("Cardiología", "Dermatología");
    }

    @Test
    void laIaNoPuedeInventarUnaEspecialidadQueNoEstaEnLaBase() throws Exception {
        laIaResponde("{\"specialty\":\"Neurología\",\"reply\":\"Te muestro neurólogos.\"}");

        ChatRecommendationResponse r = escribe("tengo un problema raro");

        assertThat(r.specialty()).isNull();
        assertThat(r.doctors()).extracting("specialty").containsOnly("Cardiología", "Dermatología");
    }

    @Test
    void sinIaLasReglasOrientanIgual() {
        when(ia.disponible()).thenReturn(false);

        ChatRecommendationResponse r = escribe("tengo acne y granos");

        assertThat(r.specialty()).isEqualTo("Dermatología");
        assertThat(r.reply()).contains("te oriento a Dermatología").contains(AVISO);
        verify(ia, never()).consultar(anyString(), anyString());
    }

    @Test
    void siLaIaNoRespondeSeExplicaQueLaEspecialidadNoTieneMedicos() {
        when(ia.disponible()).thenReturn(true);
        when(ia.consultar(anyString(), anyString())).thenReturn(null);

        ChatRecommendationResponse r = escribe("me duele la cabeza y tengo nauseas");

        // Antes respondía "No logré entender bien eso" aunque el síntoma era claro.
        assertThat(r.reply()).doesNotContain("No logré entender")
                .contains("Neurología").contains("no tenemos médicos de esa especialidad")
                .contains("Cardiología, Dermatología");
        assertThat(r.stage()).isEqualTo("ELEGIR");
        assertThat(r.options()).extracting(Option::label).contains("Cardiología", "Dermatología");
    }

    @Test
    void cambiarSoloElDiaNoConsultaALaIa() {
        when(ia.disponible()).thenReturn(true);

        ChatRecommendationResponse r = escribe("mañana");

        assertThat(r.date()).isNotNull();
        verify(ia, never()).consultar(anyString(), anyString());
    }
}
