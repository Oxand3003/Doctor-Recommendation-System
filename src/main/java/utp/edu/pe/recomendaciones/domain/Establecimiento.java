package utp.edu.pe.recomendaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Lugar donde un médico puede atender a los pacientes. */
@Entity
@Table(name = "establecimientos")
public class Establecimiento {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 150)
  private String nombre;

  @Column(length = 200)
  private String direccion;

  @Column(length = 100)
  private String distrito;

  protected Establecimiento() {
    // Constructor requerido por JPA.
  }

  public Establecimiento(String nombre, String direccion, String distrito) {
    this.nombre = nombre;
    this.direccion = direccion;
    this.distrito = distrito;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getNombre() {
    return nombre;
  }

  public void setNombre(String nombre) {
    this.nombre = nombre;
  }

  public String getDireccion() {
    return direccion;
  }

  public void setDireccion(String direccion) {
    this.direccion = direccion;
  }

  public String getDistrito() {
    return distrito;
  }

  public void setDistrito(String distrito) {
    this.distrito = distrito;
  }
}
