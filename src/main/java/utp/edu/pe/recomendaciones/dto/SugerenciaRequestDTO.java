package utp.edu.pe.recomendaciones.dto;

/** Texto libre que describe el síntoma o la zona del cuerpo afectada. */
public class SugerenciaRequestDTO {

  private String sintoma;

  public SugerenciaRequestDTO() {
  }

  public String getSintoma() {
    return sintoma;
  }

  public void setSintoma(String sintoma) {
    this.sintoma = sintoma;
  }
}
