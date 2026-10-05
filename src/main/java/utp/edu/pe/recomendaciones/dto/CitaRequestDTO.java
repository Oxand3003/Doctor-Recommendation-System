package utp.edu.pe.recomendaciones.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Datos requeridos para registrar una reserva. */
public class CitaRequestDTO {
  @NotNull(message = "El doctor es obligatorio.")
  private Long doctorId;

  @Size(max = 500, message = "El motivo no debe superar 500 caracteres.")
  private String motivo;

  @NotNull(message = "La fecha y hora de la cita son obligatorias.")
  @Future(message = "La cita debe programarse para una fecha futura.")
  private LocalDateTime fechaHora;

  @NotBlank(message = "La modalidad es obligatoria.")
  @Pattern(regexp = "PRESENCIAL|TELECONSULTA", message = "La modalidad debe ser PRESENCIAL o TELECONSULTA.")
  private String modalidad;

  public Long getDoctorId() { return doctorId; }
  public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }
  public String getMotivo() { return motivo; }
  public void setMotivo(String motivo) { this.motivo = motivo; }
  public LocalDateTime getFechaHora() { return fechaHora; }
  public void setFechaHora(LocalDateTime fechaHora) { this.fechaHora = fechaHora; }
  public String getModalidad() { return modalidad; }
  public void setModalidad(String modalidad) { this.modalidad = modalidad; }
}
