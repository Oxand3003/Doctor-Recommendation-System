package utp.edu.pe.recomendacion_citas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import utp.edu.pe.recomendaciones.domain.Especialidad;
import utp.edu.pe.recomendaciones.dto.SugerenciaDTO;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;
import utp.edu.pe.recomendaciones.service.SugerenciaEspecialidadService;

@ExtendWith(MockitoExtension.class)
class SugerenciaEspecialidadServiceTest {

  @Mock
  private EspecialidadRepository especialidadRepository;

  private SugerenciaEspecialidadService service;

  @BeforeEach
  void configurar() {
    service = new SugerenciaEspecialidadService(especialidadRepository);
  }

  private void conEspecialidadesDeEjemplo() {
    List<Especialidad> especialidades = List.of(
        conId(1L, "Cardiología"),
        conId(2L, "Dermatología"),
        conId(3L, "Pediatría"),
        conId(4L, "Medicina General"),
        conId(5L, "Odontología"),
        conId(6L, "Neurología"),
        conId(7L, "Oftalmología"),
        conId(8L, "Traumatología"),
        conId(9L, "Urología"),
        conId(10L, "Ginecología"),
        conId(11L, "Psiquiatría"),
        conId(12L, "Otorrinolaringología"),
        conId(13L, "Endocrinología"),
        conId(14L, "Gastroenterología"));
    when(especialidadRepository.findAll()).thenReturn(especialidades);
  }

  private static Especialidad conId(Long id, String nombre) {
    Especialidad especialidad = new Especialidad(nombre);
    especialidad.setId(id);
    return especialidad;
  }

  @Test
  void sugiereCardiologiaPorDolorDePecho() {
    conEspecialidadesDeEjemplo();
    SugerenciaDTO sugerencia = service.sugerir("me duele mucho el pecho y siento palpitaciones");

    assertThat(sugerencia.getEspecialidad()).isEqualTo("Cardiología");
    assertThat(sugerencia.getEspecialidadId()).isEqualTo(1L);
    assertThat(sugerencia.getConfianza()).isEqualTo(1.0);
  }

  @Test
  void ignoraTildesAlComparar() {
    conEspecialidadesDeEjemplo();
    SugerenciaDTO sugerencia = service.sugerir("CORAZÓN acelerado");

    assertThat(sugerencia.getEspecialidad()).isEqualTo("Cardiología");
  }

  @Test
  void sugiereTraumatologiaPorZonaDelCuerpo() {
    conEspecialidadesDeEjemplo();
    SugerenciaDTO sugerencia = service.sugerir("rodilla");

    assertThat(sugerencia.getEspecialidad()).isEqualTo("Traumatología");
    assertThat(sugerencia.getCoincidencias()).contains("rodilla");
  }

  @Test
  void sugiereUrologiaPorZonaPelvica() {
    conEspecialidadesDeEjemplo();
    SugerenciaDTO sugerencia = service.sugerir("Zona pélvica");

    assertThat(sugerencia.getEspecialidad()).isEqualTo("Urología");
    assertThat(sugerencia.getEspecialidadId()).isEqualTo(9L);
  }

  @Test
  void sugiereGastroenterologiaPorDolorAbdominal() {
    conEspecialidadesDeEjemplo();
    SugerenciaDTO sugerencia = service.sugerir("me duele mucho el estomago y tengo nauseas");

    assertThat(sugerencia.getEspecialidad()).isEqualTo("Gastroenterología");
  }

  @Test
  void reconoceElNombreDeLaEspecialidadEscritoDirectamente() {
    conEspecialidadesDeEjemplo();
    SugerenciaDTO sugerencia = service.sugerir("necesito un especialista en urologia");

    assertThat(sugerencia.getEspecialidad()).isEqualTo("Urología");
  }

  @Test
  void caeAMedicinaGeneralCuandoNoHayCoincidencias() {
    conEspecialidadesDeEjemplo();
    SugerenciaDTO sugerencia = service.sugerir("me siento raro hoy");

    assertThat(sugerencia.getEspecialidad()).isEqualTo("Medicina General");
    assertThat(sugerencia.getCoincidencias()).isEmpty();
  }

  @Test
  void rechazaSintomaVacioConIllegalArgument() {
    assertThatThrownBy(() -> service.sugerir(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rechazaSintomaNuloConIllegalArgument() {
    assertThatThrownBy(() -> service.sugerir(null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
