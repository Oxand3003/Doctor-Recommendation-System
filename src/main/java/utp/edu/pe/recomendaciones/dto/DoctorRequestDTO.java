package utp.edu.pe.recomendaciones.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Datos que el cliente envia para crear o actualizar un medico.
 * Las validaciones se evaluan con @Valid en el controller.
 */
public class DoctorRequestDTO {

  @NotBlank(message = "Los nombres son obligatorios.")
  @Size(max = 100, message = "Los nombres no deben superar 100 caracteres.")
  private String nombres;

  @NotBlank(message = "Los apellidos son obligatorios.")
  @Size(max = 100, message = "Los apellidos no deben superar 100 caracteres.")
  private String apellidos;

  @NotBlank(message = "El CMP es obligatorio.")
  @Size(max = 20, message = "El CMP no debe superar 20 caracteres.")
  private String cmp;

  @NotNull(message = "El rating es obligatorio.")
  @DecimalMin(value = "0.0", message = "El rating no puede ser negativo.")
  @DecimalMax(value = "5.0", message = "El rating no puede superar 5.0.")
  private Double rating;

  @NotNull(message = "Los anios de experiencia son obligatorios.")
  @Min(value = 0, message = "Los anios de experiencia no pueden ser negativos.")
  @Max(value = 80, message = "Los anios de experiencia no pueden superar 80.")
  private Integer aniosExperiencia;

  private Boolean disponible = Boolean.TRUE;

  @NotNull(message = "La especialidad es obligatoria.")
  private Long especialidadId;

  private Long establecimientoId;

  public String getNombres() {
    return nombres;
  }

  public void setNombres(String nombres) {
    this.nombres = nombres;
  }

  public String getApellidos() {
    return apellidos;
  }

  public void setApellidos(String apellidos) {
    this.apellidos = apellidos;
  }

  public String getCmp() {
    return cmp;
  }

  public void setCmp(String cmp) {
    this.cmp = cmp;
  }

  public Double getRating() {
    return rating;
  }

  public void setRating(Double rating) {
    this.rating = rating;
  }

  public Integer getAniosExperiencia() {
    return aniosExperiencia;
  }

  public void setAniosExperiencia(Integer aniosExperiencia) {
    this.aniosExperiencia = aniosExperiencia;
  }

  public Boolean getDisponible() {
    return disponible;
  }

  public void setDisponible(Boolean disponible) {
    this.disponible = disponible;
  }

  public Long getEspecialidadId() {
    return especialidadId;
  }

  public void setEspecialidadId(Long especialidadId) {
    this.especialidadId = especialidadId;
  }

  public Long getEstablecimientoId() {
    return establecimientoId;
  }

  public void setEstablecimientoId(Long establecimientoId) {
    this.establecimientoId = establecimientoId;
  }
}
