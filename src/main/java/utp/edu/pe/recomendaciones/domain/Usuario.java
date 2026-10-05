package utp.edu.pe.recomendaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Cuenta de acceso de un cliente, médico o administrador. */
@Entity
@Table(name = "usuarios")
public class Usuario {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String nombres;

  @Column(nullable = false, length = 100)
  private String apellidos;

  @Column(nullable = false, unique = true, length = 12)
  private String dni;

  @Column(nullable = false, unique = true, length = 190)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(nullable = false, length = 30)
  private String telefono;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RolUsuario rol;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoUsuario estado;

  @Column(name = "creado_en", nullable = false)
  private LocalDateTime creadoEn;

  protected Usuario() {
  }

  public Usuario(String nombres, String apellidos, String dni, String email, String passwordHash,
      String telefono, RolUsuario rol, EstadoUsuario estado) {
    this.nombres = nombres;
    this.apellidos = apellidos;
    this.dni = dni;
    this.email = email;
    this.passwordHash = passwordHash;
    this.telefono = telefono;
    this.rol = rol;
    this.estado = estado;
  }

  @PrePersist
  void alCrear() {
    if (creadoEn == null) creadoEn = LocalDateTime.now();
  }

  public Long getId() { return id; }
  public String getNombres() { return nombres; }
  public String getApellidos() { return apellidos; }
  public String getDni() { return dni; }
  public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; }
  public String getTelefono() { return telefono; }
  public RolUsuario getRol() { return rol; }
  public EstadoUsuario getEstado() { return estado; }
  public LocalDateTime getCreadoEn() { return creadoEn; }
  public void setEstado(EstadoUsuario estado) { this.estado = estado; }
}
