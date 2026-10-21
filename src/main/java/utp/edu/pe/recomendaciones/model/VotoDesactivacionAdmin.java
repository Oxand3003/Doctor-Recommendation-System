package utp.edu.pe.recomendaciones.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Voto a favor de desactivar una cuenta de administrador. Un administrador solo puede votar una vez por solicitud. */
@Entity
@Table(name = "votos_desactivacion_admin",
        uniqueConstraints = @UniqueConstraint(name = "uk_voto_desact_admin", columnNames = {"solicitud_id", "votante_id"}))
public class VotoDesactivacionAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false)
    private SolicitudDesactivacionAdmin solicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "votante_id", nullable = false)
    private Usuario votante;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    public VotoDesactivacionAdmin() { }

    @PrePersist
    void prePersist() {
        if (creadoEn == null) creadoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public SolicitudDesactivacionAdmin getSolicitud() { return solicitud; }
    public void setSolicitud(SolicitudDesactivacionAdmin solicitud) { this.solicitud = solicitud; }
    public Usuario getVotante() { return votante; }
    public void setVotante(Usuario votante) { this.votante = votante; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
