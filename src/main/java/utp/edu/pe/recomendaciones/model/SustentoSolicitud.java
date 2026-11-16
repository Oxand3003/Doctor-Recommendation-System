package utp.edu.pe.recomendaciones.model;

import jakarta.persistence.*;

/**
 * Archivo de sustento (constancia CMP, RNE o título) que el médico adjunta a su solicitud de registro.
 * Se guarda en la base de datos y no en disco: en Render el disco se borra en cada despliegue y el administrador
 * podría no llegar a verlo. Tabla aparte para que el panel no cargue el archivo al listar solicitudes.
 */
@Entity
@Table(name = "sustentos_solicitud")
public class SustentoSolicitud {

    /** 5 MB de archivo + margen; en MySQL queda como MEDIUMBLOB. */
    public static final int MAX_COLUMNA = 6 * 1024 * 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false, unique = true)
    private SolicitudVerificacion solicitud;

    /** Nombre original del archivo (solo para mostrarlo y sugerirlo al descargar). */
    @Column(nullable = false, length = 200)
    private String nombre;

    /** Tipo detectado por los bytes del archivo: application/pdf, image/jpeg, image/png o image/webp. */
    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(nullable = false)
    private long tamano;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, length = MAX_COLUMNA)
    private byte[] datos;

    public Long getId() {
        return id;
    }

    public SolicitudVerificacion getSolicitud() {
        return solicitud;
    }

    public void setSolicitud(SolicitudVerificacion solicitud) {
        this.solicitud = solicitud;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public long getTamano() {
        return tamano;
    }

    public void setTamano(long tamano) {
        this.tamano = tamano;
    }

    public byte[] getDatos() {
        return datos;
    }

    public void setDatos(byte[] datos) {
        this.datos = datos;
    }
}
