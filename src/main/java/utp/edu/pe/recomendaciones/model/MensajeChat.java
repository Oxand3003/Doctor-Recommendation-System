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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/** Mensaje persistente; su texto no se edita y solo cambia leidoEn al marcarlo como leído. */
@Entity
@Table(name = "mensajes_chat",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_mensaje_client_id",
                columnNames = {"conversacion_id", "client_id"}),
        indexes = @jakarta.persistence.Index(name = "idx_chat_mensajes_conversacion_fecha",
                columnList = "conversacion_id, creado_en"))
public class MensajeChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversacion_id", nullable = false)
    private Conversacion conversacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "emisor_id", nullable = false)
    private Usuario emisor;

    @Column(nullable = false, length = 5000)
    private String texto;

    /** Idempotency key provided by the client when retrying a send. */
    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "leido_en")
    private LocalDateTime leidoEn;

    public MensajeChat() {
    }

    public MensajeChat(Conversacion conversacion, Usuario emisor, String texto, String clientId) {
        this.conversacion = conversacion;
        this.emisor = emisor;
        this.texto = texto;
        this.clientId = clientId;
    }

    @PrePersist
    void prePersist() {
        if (creadoEn == null) creadoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Conversacion getConversacion() { return conversacion; }
    public Usuario getEmisor() { return emisor; }
    public String getTexto() { return texto; }
    public String getClientId() { return clientId; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getLeidoEn() { return leidoEn; }
    public void setLeidoEn(LocalDateTime leidoEn) { this.leidoEn = leidoEn; }
}
