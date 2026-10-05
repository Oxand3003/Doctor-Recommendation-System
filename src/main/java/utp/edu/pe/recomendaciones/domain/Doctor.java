package utp.edu.pe.recomendaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Información necesaria para recomendar y mostrar a un médico. */
@Entity
@Table(name = "doctores")
public class Doctor {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String nombres;

  @Column(nullable = false, length = 100)
  private String apellidos;

  @Column(nullable = false, unique = true, length = 20)
  private String cmp;

  @Column(nullable = false)
  private Double rating = 0.0;

  @Column(nullable = false)
  private Integer aniosExperiencia = 0;

  @Column(nullable = false)
  private Boolean disponible = true;

  @Column(length = 20)
  private String rne;

  @Column(name = "titulo_profesional", length = 150)
  private String tituloProfesional;

  @Column(length = 180)
  private String universidad;

  @Column(name = "anio_egreso")
  private Integer anioEgreso;

  @Column(name = "sustento_url", length = 500)
  private String sustentoUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "estado_verificacion", nullable = false, length = 20)
  private EstadoVerificacionMedico estadoVerificacion = EstadoVerificacionMedico.APROBADO;

  @Column(name = "motivo_rechazo", length = 500)
  private String motivoRechazo;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "usuario_id", unique = true)
  private Usuario usuario;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "especialidad_id", nullable = false)
  private Especialidad especialidad;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "establecimiento_id")
  private Establecimiento establecimiento;

  protected Doctor() {
    // Constructor requerido por JPA.
  }

  public Doctor(
      String nombres,
      String apellidos,
      String cmp,
      Double rating,
      Integer aniosExperiencia,
      Boolean disponible,
      Especialidad especialidad,
      Establecimiento establecimiento) {
    this.nombres = nombres;
    this.apellidos = apellidos;
    this.cmp = cmp;
    this.rating = rating;
    this.aniosExperiencia = aniosExperiencia;
    this.disponible = disponible;
    this.especialidad = especialidad;
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

  public Especialidad getEspecialidad() {
    return especialidad;
  }

  public void setEspecialidad(Especialidad especialidad) {
    this.especialidad = especialidad;
  }

  public Establecimiento getEstablecimiento() {
    return establecimiento;
  }

  public void setEstablecimiento(Establecimiento establecimiento) {
    this.establecimiento = establecimiento;
  }

  public String getRne() { return rne; }
  public void setRne(String rne) { this.rne = rne; }
  public String getTituloProfesional() { return tituloProfesional; }
  public void setTituloProfesional(String tituloProfesional) { this.tituloProfesional = tituloProfesional; }
  public String getUniversidad() { return universidad; }
  public void setUniversidad(String universidad) { this.universidad = universidad; }
  public Integer getAnioEgreso() { return anioEgreso; }
  public void setAnioEgreso(Integer anioEgreso) { this.anioEgreso = anioEgreso; }
  public String getSustentoUrl() { return sustentoUrl; }
  public void setSustentoUrl(String sustentoUrl) { this.sustentoUrl = sustentoUrl; }
  public EstadoVerificacionMedico getEstadoVerificacion() { return estadoVerificacion; }
  public void setEstadoVerificacion(EstadoVerificacionMedico estadoVerificacion) { this.estadoVerificacion = estadoVerificacion; }
  public String getMotivoRechazo() { return motivoRechazo; }
  public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }
  public Usuario getUsuario() { return usuario; }
  public void setUsuario(Usuario usuario) { this.usuario = usuario; }
}
