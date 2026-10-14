package utp.edu.pe.recomendaciones.dto;

import java.util.List;

/** Especialidad sugerida para un síntoma, junto con el resto del ranking. */
public class SugerenciaDTO {

  private Long especialidadId;
  private String especialidad;
  private double confianza;
  private List<String> coincidencias;
  private List<SugerenciaDTO> alternativas;

  public SugerenciaDTO() {
  }

  public SugerenciaDTO(
      Long especialidadId,
      String especialidad,
      double confianza,
      List<String> coincidencias,
      List<SugerenciaDTO> alternativas) {
    this.especialidadId = especialidadId;
    this.especialidad = especialidad;
    this.confianza = confianza;
    this.coincidencias = coincidencias;
    this.alternativas = alternativas;
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

  public double getConfianza() {
    return confianza;
  }

  public void setConfianza(double confianza) {
    this.confianza = confianza;
  }

  public List<String> getCoincidencias() {
    return coincidencias;
  }

  public void setCoincidencias(List<String> coincidencias) {
    this.coincidencias = coincidencias;
  }

  public List<SugerenciaDTO> getAlternativas() {
    return alternativas;
  }

  public void setAlternativas(List<SugerenciaDTO> alternativas) {
    this.alternativas = alternativas;
  }
}
