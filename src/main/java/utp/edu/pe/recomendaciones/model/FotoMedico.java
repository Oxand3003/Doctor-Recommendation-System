package utp.edu.pe.recomendaciones.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import utp.edu.pe.recomendaciones.model.enums.TipoFoto;

/** Metadatos de una fotografía del médico. El archivo vive en disco (ver FotoStorageService). */
@Entity
@Table(name = "fotos_medico")
public class FotoMedico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoFoto tipo;

    /** Nombre generado (UUID + extensión) con el que se guarda el archivo. Nunca viene del usuario. */
    @Column(nullable = false, unique = true, length = 60)
    private String archivo;

    @Column(nullable = false, length = 50)
    private String tipoContenido;

    @Column(nullable = false)
    private long tamanoBytes;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    public FotoMedico() {
    }

    @PrePersist
    void prePersist() {
        if (creadoEn == null) creadoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Medico getMedico() { return medico; }
    public void setMedico(Medico medico) { this.medico = medico; }
    public TipoFoto getTipo() { return tipo; }
    public void setTipo(TipoFoto tipo) { this.tipo = tipo; }
    public String getArchivo() { return archivo; }
    public void setArchivo(String archivo) { this.archivo = archivo; }
    public String getTipoContenido() { return tipoContenido; }
    public void setTipoContenido(String tipoContenido) { this.tipoContenido = tipoContenido; }
    public long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(long tamanoBytes) { this.tamanoBytes = tamanoBytes; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
