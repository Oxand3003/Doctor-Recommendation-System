package utp.edu.pe.recomendaciones.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import utp.edu.pe.recomendaciones.model.enums.EstadoDesactivacionAdmin;

/**
 * Solicitud para desactivar (suspender) una cuenta de administrador. Se ejecuta solo cuando el número requerido
 * de administradores DISTINTOS (sin contar la cuenta afectada) la aprueban; quien la inicia cuenta como el primer voto.
 */
@Entity
@Table(name = "solicitudes_desactivacion_admin", indexes = {
        @Index(name = "idx_desact_admin_objetivo", columnList = "objetivo_id"),
        @Index(name = "idx_desact_admin_estado", columnList = "estado")})
public class SolicitudDesactivacionAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "objetivo_id", nullable = false)
    private Usuario objetivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "iniciador_id", nullable = false)
    private Usuario iniciador;

    @Column(nullable = false, length = 300)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDesactivacionAdmin estado = EstadoDesactivacionAdmin.PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resuelto_por_id")
    private Usuario resueltoPor;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;

    public SolicitudDesactivacionAdmin() { }

    @PrePersist
    void prePersist() {
        if (creadoEn == null) creadoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Usuario getObjetivo() { return objetivo; }
    public void setObjetivo(Usuario objetivo) { this.objetivo = objetivo; }
    public Usuario getIniciador() { return iniciador; }
    public void setIniciador(Usuario iniciador) { this.iniciador = iniciador; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public EstadoDesactivacionAdmin getEstado() { return estado; }
    public void setEstado(EstadoDesactivacionAdmin estado) { this.estado = estado; }
    public Usuario getResueltoPor() { return resueltoPor; }
    public void setResueltoPor(Usuario resueltoPor) { this.resueltoPor = resueltoPor; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getResueltoEn() { return resueltoEn; }
    public void setResueltoEn(LocalDateTime resueltoEn) { this.resueltoEn = resueltoEn; }
}
