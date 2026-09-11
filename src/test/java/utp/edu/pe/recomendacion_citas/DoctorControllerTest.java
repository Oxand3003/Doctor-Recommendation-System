package utp.edu.pe.recomendacion_citas;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import utp.edu.pe.recomendaciones.exception.ApiExceptionHandler;
import utp.edu.pe.recomendaciones.controller.DoctorController;
import utp.edu.pe.recomendaciones.domain.Doctor;
import utp.edu.pe.recomendaciones.domain.Especialidad;
import utp.edu.pe.recomendaciones.dto.RecomendacionDTO;
import utp.edu.pe.recomendaciones.exception.DoctorNotFoundException;
import utp.edu.pe.recomendaciones.service.DoctorService;
import utp.edu.pe.recomendaciones.service.RecomendacionService;

@ExtendWith(MockitoExtension.class)
class DoctorControllerTest {

  private MockMvc mockMvc;

  @Mock
  private DoctorService doctorService;

  @Mock
  private RecomendacionService recomendacionService;

  @BeforeEach
  void configurarMockMvc() {
    mockMvc = MockMvcBuilders.standaloneSetup(
            new DoctorController(doctorService, recomendacionService))
        .setControllerAdvice(new ApiExceptionHandler())
        .build();
  }

  @Test
  void rechazaLimiteInvalidoConBadRequest() throws Exception {
    mockMvc.perform(get("/api/v1/doctors/recomendar")
            .param("especialidad", "Cardiologia")
            .param("limite", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Bad Request"));
  }

      @Test
      void rechazaEspecialidadAusenteConBadRequest() throws Exception {
      mockMvc.perform(get("/api/v1/doctors/recomendar"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Bad Request"));
      }

      @Test
      void listaDoctoresConOk() throws Exception {
      Doctor doctor = new Doctor(
        "María",
        "Fernández Torres",
        "CMP10001",
        4.9,
        15,
        true,
        new Especialidad("Cardiología"),
        null);
      when(doctorService.listarTodos()).thenReturn(List.of(doctor));

      mockMvc.perform(get("/api/v1/doctors"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].nombres").value("María"))
        .andExpect(jsonPath("$[0].cmp").value("CMP10001"));
      }

      @Test
      void obtieneDoctorPorIdConOk() throws Exception {
      Doctor doctor = new Doctor(
        "Carlos",
        "Ramírez Soto",
        "CMP10002",
        4.7,
        20,
        true,
        new Especialidad("Cardiología"),
        null);
      when(doctorService.buscarPorId(1L)).thenReturn(doctor);

      mockMvc.perform(get("/api/v1/doctors/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.apellidos").value("Ramírez Soto"));
      }

      @Test
      void recomiendaDoctoresConOk() throws Exception {
      RecomendacionDTO recomendacion = new RecomendacionDTO(
        1L,
        "María Fernández Torres",
        "CMP10001",
        "Cardiología",
        4.9,
        15,
        "Clínica San Felipe",
        91.5);
      when(recomendacionService.recomendarPorEspecialidad("Cardiología", 5))
        .thenReturn(List.of(recomendacion));

      mockMvc.perform(get("/api/v1/doctors/recomendar")
          .param("especialidad", "Cardiología")
          .param("limite", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].doctorId").value(1))
        .andExpect(jsonPath("$[0].nombreCompleto").value("María Fernández Torres"));
      }

  @Test
  void devuelveNotFoundCuandoNoHayDoctores() throws Exception {
    when(recomendacionService.recomendarPorEspecialidad("Neurologia", 5))
        .thenThrow(new DoctorNotFoundException("No se encontraron medicos disponibles."));

    mockMvc.perform(get("/api/v1/doctors/recomendar")
            .param("especialidad", "Neurologia")
            .param("limite", "5"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Not Found"));
  }

  @Test
  void devuelveNotFoundCuandoNoExisteDoctorPorId() throws Exception {
    when(doctorService.buscarPorId(99L))
        .thenThrow(new DoctorNotFoundException("No existe un medico con id: 99"));

    mockMvc.perform(get("/api/v1/doctors/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Not Found"));
  }
}
