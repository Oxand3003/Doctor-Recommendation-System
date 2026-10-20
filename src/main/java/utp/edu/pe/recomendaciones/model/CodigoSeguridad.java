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
import utp.edu.pe.recomendaciones.model.enums.PropositoCodigo;

/**
 * Código de seguridad de un solo uso (6 dígitos) enviado al correo registrado de un usuario antes de permitirle
 * cambiar sus credenciales. El código NUNCA se guarda en claro: solo su hash BCrypt. Caduca, tiene un número
 * máximo de intentos y queda "consumido" al usarse, al agotar los intentos o al emitirse uno nuevo.
 */
@Entity
@Table(name = "codigos_seguridad", indexes = {
        @Index(name = "idx_codigos_usuario_proposito", columnList = "usuario_id, proposito")
})
public class CodigoSeguridad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PropositoCodigo proposito;

    @Column(nullable = false, length = 100)
    private String codigoHash;

    /** Solo para CAMBIO_EMAIL: el correo nuevo que quedó ligado a este código al pedirlo. */
    @Column(length = 190)
    private String nuevoEmail;

    @Column(length = 255)
    private String valorPendiente;

    @Column(nullable = false)
    private int intentos;

    @Column(nullable = false)
    private boolean consumido;

    @Column(nullable = false)
    private LocalDateTime creadoEn;

    @Column(nullable = false)
    private LocalDateTime expiraEn;

    @Column
    private LocalDateTime bloqueadoHasta;

    public CodigoSeguridad() {
    }

    @PrePersist
    void prePersist() {
        if (creadoEn == null) creadoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public PropositoCodigo getProposito() { return proposito; }
    public void setProposito(PropositoCodigo proposito) { this.proposito = proposito; }

    public String getCodigoHash() { return codigoHash; }
    public void setCodigoHash(String codigoHash) { this.codigoHash = codigoHash; }

    public String getNuevoEmail() { return nuevoEmail; }
    public void setNuevoEmail(String nuevoEmail) { this.nuevoEmail = nuevoEmail; }
    public String getValorPendiente() { return valorPendiente; }
    public void setValorPendiente(String valorPendiente) { this.valorPendiente = valorPendiente; }

    public int getIntentos() { return intentos; }
    public void setIntentos(int intentos) { this.intentos = intentos; }

    public boolean isConsumido() { return consumido; }
    public void setConsumido(boolean consumido) { this.consumido = consumido; }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }

    public LocalDateTime getExpiraEn() { return expiraEn; }
    public void setExpiraEn(LocalDateTime expiraEn) { this.expiraEn = expiraEn; }
    public LocalDateTime getBloqueadoHasta() { return bloqueadoHasta; }
    public void setBloqueadoHasta(LocalDateTime bloqueadoHasta) { this.bloqueadoHasta = bloqueadoHasta; }
}
