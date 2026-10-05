package utp.edu.pe.recomendaciones.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegistroMedicoDTO extends RegistroClienteDTO {
  @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{4,20}", message = "Ingresa un número CMP válido.")
  private String cmp;
  @Size(max = 20)
  private String rne;
  @NotBlank @Size(max = 150)
  private String tituloProfesional;
  @NotBlank @Size(max = 180)
  private String universidad;
  @NotNull @Min(1950) @Max(2100)
  private Integer anioEgreso;
  @NotNull @Min(0) @Max(80)
  private Integer aniosExperiencia;
  @NotNull
  private Long especialidadId;
  private Long establecimientoId;
  @NotBlank @Size(max = 500)
  @Pattern(regexp = "https?://.+", message = "Ingresa un enlace http o https a la constancia profesional.")
  private String sustentoUrl;

  public String getCmp() { return cmp; }
  public void setCmp(String cmp) { this.cmp = cmp; }
  public String getRne() { return rne; }
  public void setRne(String rne) { this.rne = rne; }
  public String getTituloProfesional() { return tituloProfesional; }
  public void setTituloProfesional(String tituloProfesional) { this.tituloProfesional = tituloProfesional; }
  public String getUniversidad() { return universidad; }
  public void setUniversidad(String universidad) { this.universidad = universidad; }
  public Integer getAnioEgreso() { return anioEgreso; }
  public void setAnioEgreso(Integer anioEgreso) { this.anioEgreso = anioEgreso; }
  public Integer getAniosExperiencia() { return aniosExperiencia; }
  public void setAniosExperiencia(Integer aniosExperiencia) { this.aniosExperiencia = aniosExperiencia; }
  public Long getEspecialidadId() { return especialidadId; }
  public void setEspecialidadId(Long especialidadId) { this.especialidadId = especialidadId; }
  public Long getEstablecimientoId() { return establecimientoId; }
  public void setEstablecimientoId(Long establecimientoId) { this.establecimientoId = establecimientoId; }
  public String getSustentoUrl() { return sustentoUrl; }
  public void setSustentoUrl(String sustentoUrl) { this.sustentoUrl = sustentoUrl; }
}
