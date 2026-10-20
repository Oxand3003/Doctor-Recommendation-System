package utp.edu.pe.recomendaciones.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** Conversación privada y persistente entre un único paciente y un único médico. */
@Entity
@Table(name = "conversaciones_chat",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_paciente_medico",
                columnNames = {"paciente_id", "medico_id"}),
        indexes = @jakarta.persistence.Index(name = "idx_chat_conversaciones_medico", columnList = "medico_id"))
public class Conversacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Usuario paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    public Conversacion() {
    }

    public Conversacion(Usuario paciente, Medico medico) {
        this.paciente = paciente;
        this.medico = medico;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime ahora = LocalDateTime.now();
        if (creadoEn == null) creadoEn = ahora;
        if (actualizadoEn == null) actualizadoEn = ahora;
    }

    @PreUpdate
    void preUpdate() {
        actualizadoEn = LocalDateTime.now();
    }

    /** Actualiza el orden de la bandeja cuando se añade un mensaje. */
    public void marcarActividad() {
        actualizadoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Usuario getPaciente() { return paciente; }
    public Medico getMedico() { return medico; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
}
