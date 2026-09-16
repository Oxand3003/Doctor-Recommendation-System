package utp.edu.pe.recomendaciones.dto;

/** Datos que el cliente envía para crear o actualizar un médico. */
public class DoctorRequest {

  private String nombres;
  private String apellidos;
  private String cmp;
  private Double rating;
  private Integer aniosExperiencia;
  private Boolean disponible;
  private Long especialidadId;
  private Long establecimientoId;

  public DoctorRequest() {
  }

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
