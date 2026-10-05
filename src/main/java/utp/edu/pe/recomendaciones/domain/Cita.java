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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** Reserva de atención solicitada por un paciente. */
@Entity
@Table(name = "citas", uniqueConstraints = @UniqueConstraint(
    name = "uq_citas_doctor_fecha", columnNames = {"doctor_id", "fecha_hora"}))
public class Cita {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "paciente_nombre", nullable = false, length = 150)
  private String pacienteNombre;

  @Column(name = "paciente_dni", nullable = false, length = 12)
  private String pacienteDni;

  @Column(name = "paciente_telefono", nullable = false, length = 30)
  private String pacienteTelefono;

  @Column(length = 500)
  private String motivo;

  @Column(name = "fecha_hora", nullable = false)
  private LocalDateTime fechaHora;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ModalidadCita modalidad;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EstadoCita estado;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "doctor_id", nullable = false)
  private Doctor doctor;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "cliente_id", nullable = false)
  private Usuario cliente;

  protected Cita() {
  }

  public Cita(
      String pacienteNombre,
      String pacienteDni,
      String pacienteTelefono,
      String motivo,
      LocalDateTime fechaHora,
      ModalidadCita modalidad,
      Doctor doctor,
      Usuario cliente) {
    this.pacienteNombre = pacienteNombre;
    this.pacienteDni = pacienteDni;
    this.pacienteTelefono = pacienteTelefono;
    this.motivo = motivo;
    this.fechaHora = fechaHora;
    this.modalidad = modalidad;
    this.estado = EstadoCita.CONFIRMADA;
    this.doctor = doctor;
    this.cliente = cliente;
  }

  public Long getId() { return id; }
  public String getPacienteNombre() { return pacienteNombre; }
  public String getPacienteDni() { return pacienteDni; }
  public String getPacienteTelefono() { return pacienteTelefono; }
  public String getMotivo() { return motivo; }
  public LocalDateTime getFechaHora() { return fechaHora; }
  public ModalidadCita getModalidad() { return modalidad; }
  public EstadoCita getEstado() { return estado; }
  public Doctor getDoctor() { return doctor; }
  public Usuario getCliente() { return cliente; }
}
