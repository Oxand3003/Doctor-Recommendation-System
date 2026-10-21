package utp.edu.pe.recomendaciones.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import utp.edu.pe.recomendaciones.model.enums.CanalAtencion;
import utp.edu.pe.recomendaciones.model.enums.EstadoConsulta;
import utp.edu.pe.recomendaciones.model.enums.MotivoConsulta;

/** Consulta, reclamo o solicitud enviada desde el formulario de contacto del inicio. */
@Entity
@Table(name = "consultas", indexes = {
        @Index(name = "idx_consultas_estado", columnList = "estado"),
        @Index(name = "idx_consultas_correo", columnList = "correo")
})
public class Consulta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombres;

    @Column(nullable = false, length = 150)
    private String correo;

    @Column(nullable = false, length = 30)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MotivoConsulta motivo;

    @Column(nullable = false, length = 2000)
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoConsulta estado = EstadoConsulta.PENDIENTE;

    /** Cuenta que envió la consulta, si tenía la sesión iniciada (puede ser null). */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    @Column
    private LocalDateTime fechaAtencion;

    /** Por dónde se contactó al usuario al atender la consulta. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CanalAtencion canalAtencion;

    /** Correo del administrador que la atendió. */
    @Column(length = 150)
    private String atendidaPor;

    /** Último mensaje enviado por correo al usuario desde el panel (null si no se respondió por correo). */
    @Column(length = 3000)
    private String respuesta;

    public Consulta() {
    }

    @PrePersist
    void prePersist() {
        if (fechaCreacion == null) fechaCreacion = LocalDateTime.now();
        if (estado == null) estado = EstadoConsulta.PENDIENTE;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public MotivoConsulta getMotivo() { return motivo; }
    public void setMotivo(MotivoConsulta motivo) { this.motivo = motivo; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public EstadoConsulta getEstado() { return estado; }
    public void setEstado(EstadoConsulta estado) { this.estado = estado; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getFechaAtencion() { return fechaAtencion; }
    public void setFechaAtencion(LocalDateTime fechaAtencion) { this.fechaAtencion = fechaAtencion; }

    public CanalAtencion getCanalAtencion() { return canalAtencion; }
    public void setCanalAtencion(CanalAtencion canalAtencion) { this.canalAtencion = canalAtencion; }

    public String getAtendidaPor() { return atendidaPor; }
    public void setAtendidaPor(String atendidaPor) { this.atendidaPor = atendidaPor; }

    public String getRespuesta() { return respuesta; }
    public void setRespuesta(String respuesta) { this.respuesta = respuesta; }
}
