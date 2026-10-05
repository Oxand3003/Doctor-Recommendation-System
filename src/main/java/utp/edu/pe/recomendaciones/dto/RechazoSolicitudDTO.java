package utp.edu.pe.recomendaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RechazoSolicitudDTO {
  @NotBlank @Size(max = 500)
  private String motivo;

  public String getMotivo() { return motivo; }
  public void setMotivo(String motivo) { this.motivo = motivo; }
}
