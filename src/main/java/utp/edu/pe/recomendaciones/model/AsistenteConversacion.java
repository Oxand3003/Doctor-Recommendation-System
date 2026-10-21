package utp.edu.pe.recomendaciones.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Conversación vigente del paciente con el asistente de IA (una por usuario). Guarda los mensajes y lo que el
 * asistente ya entendió (especialidad, fecha, etc.) para retomarla al volver. "Nueva conversación" la borra.
 * Se enlaza por usuario_id sin clave foránea para no interferir con el borrado de cuentas.
 */
@Entity
@Table(name = "asistente_conversaciones")
public class AsistenteConversacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, unique = true)
    private Long usuarioId;

    /** Mensajes en JSON: [{"role":"user","content":"..."}, ...]. */
    @Lob
    @Column(name = "mensajes", columnDefinition = "LONGTEXT")
    private String mensajes = "[]";

    @Column(length = 100)
    private String especialidad;

    @Column(length = 100)
    private String distrito;

    private LocalDate fecha;

    private LocalTime hora;

    /** "rating" (mejor calificados) o "price" (menor precio). */
    @Column(length = 10)
    private String orden = "rating";

    /** Punto del flujo: MENU, SINTOMAS, ELEGIR o LIBRE. */
    @Column(length = 20)
    private String etapa = "MENU";

    /** Para qué se elige especialidad: FILTRO, PRECIO o CALIFICACION. */
    @Column(length = 20)
    private String proposito;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    public AsistenteConversacion() { }

    @PrePersist
    @PreUpdate
    void marcarActualizacion() {
        actualizadoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public String getMensajes() { return mensajes; }
    public void setMensajes(String mensajes) { this.mensajes = mensajes; }
    public String getEspecialidad() { return especialidad; }
    public void setEspecialidad(String especialidad) { this.especialidad = especialidad; }
    public String getDistrito() { return distrito; }
    public void setDistrito(String distrito) { this.distrito = distrito; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }
    public String getOrden() { return orden; }
    public void setOrden(String orden) { this.orden = orden; }
    public String getEtapa() { return etapa; }
    public void setEtapa(String etapa) { this.etapa = etapa; }
    public String getProposito() { return proposito; }
    public void setProposito(String proposito) { this.proposito = proposito; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
}
