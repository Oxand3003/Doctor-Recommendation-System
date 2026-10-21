package utp.edu.pe.recomendaciones.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import utp.edu.pe.recomendaciones.model.enums.EstadoSolicitud;

@Entity
@Table(name = "solicitudes_verificacion")
public class SolicitudVerificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @Column(nullable = false, length = 500)
    private String sustentoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @Column(length = 500)
    private String motivoRechazo;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "revisado_por")
    private Usuario revisadoPor;

    @Column(nullable = false)
    private LocalDateTime fechaSolicitud;

    @Column()
    private LocalDateTime fechaRevision;

    public SolicitudVerificacion() {
    }

    @PrePersist
    void prePersist() {
        if (fechaSolicitud == null) fechaSolicitud = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Medico getMedico() {
        return medico;
    }

    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    public String getSustentoUrl() {
        return sustentoUrl;
    }

    public void setSustentoUrl(String sustentoUrl) {
        this.sustentoUrl = sustentoUrl;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }

    public void setEstado(EstadoSolicitud estado) {
        this.estado = estado;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public Usuario getRevisadoPor() {
        return revisadoPor;
    }

    public void setRevisadoPor(Usuario revisadoPor) {
        this.revisadoPor = revisadoPor;
    }

    public LocalDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(LocalDateTime fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    public LocalDateTime getFechaRevision() {
        return fechaRevision;
    }

    public void setFechaRevision(LocalDateTime fechaRevision) {
        this.fechaRevision = fechaRevision;
    }

}
