package utp.edu.pe.recomendaciones.dto;

/**
 * Datos que se exponen al cliente cuando consulta un medico.
 * Evita devolver la entidad JPA y con ella el proxy de Hibernate.
 */
public class DoctorDTO {

  private Long id;
  private String nombres;
  private String apellidos;
  private String cmp;
  private Double rating;
  private Integer aniosExperiencia;
  private Boolean disponible;
  private Long especialidadId;
  private String especialidad;
  private Long establecimientoId;
  private String establecimiento;

  public DoctorDTO() {
  }

  public DoctorDTO(
      Long id,
      String nombres,
      String apellidos,
      String cmp,
      Double rating,
      Integer aniosExperiencia,
      Boolean disponible,
      Long especialidadId,
      String especialidad,
      Long establecimientoId,
      String establecimiento) {
    this.id = id;
    this.nombres = nombres;
    this.apellidos = apellidos;
    this.cmp = cmp;
    this.rating = rating;
    this.aniosExperiencia = aniosExperiencia;
    this.disponible = disponible;
    this.especialidadId = especialidadId;
    this.especialidad = especialidad;
    this.establecimientoId = establecimientoId;
    this.establecimiento = establecimiento;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
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

  public String getEspecialidad() {
    return especialidad;
  }

  public void setEspecialidad(String especialidad) {
    this.especialidad = especialidad;
  }

  public Long getEstablecimientoId() {
    return establecimientoId;
  }

  public void setEstablecimientoId(Long establecimientoId) {
    this.establecimientoId = establecimientoId;
  }

  public String getEstablecimiento() {
    return establecimiento;
  }

  public void setEstablecimiento(String establecimiento) {
    this.establecimiento = establecimiento;
  }
}
