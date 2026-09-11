package utp.edu.pe.recomendaciones.dto;

/** Datos que se exponen al cliente al recomendar un médico. */
public class RecomendacionDTO {

  private final Long doctorId;
  private final String nombreCompleto;
  private final String cmp;
  private final String especialidad;
  private final Double rating;
  private final Integer aniosExperiencia;
  private final String establecimiento;
  private final double puntajeRecomendacion;

  public RecomendacionDTO(
      Long doctorId,
      String nombreCompleto,
      String cmp,
      String especialidad,
      Double rating,
      Integer aniosExperiencia,
      String establecimiento,
      double puntajeRecomendacion) {
    this.doctorId = doctorId;
    this.nombreCompleto = nombreCompleto;
    this.cmp = cmp;
    this.especialidad = especialidad;
    this.rating = rating;
    this.aniosExperiencia = aniosExperiencia;
    this.establecimiento = establecimiento;
    this.puntajeRecomendacion = puntajeRecomendacion;
  }

  public Long getDoctorId() {
    return doctorId;
  }

  public String getNombreCompleto() {
    return nombreCompleto;
  }

  public String getCmp() {
    return cmp;
  }

  public String getEspecialidad() {
    return especialidad;
  }

  public Double getRating() {
    return rating;
  }

  public Integer getAniosExperiencia() {
    return aniosExperiencia;
  }

  public String getEstablecimiento() {
    return establecimiento;
  }

  public double getPuntajeRecomendacion() {
    return puntajeRecomendacion;
  }
}
