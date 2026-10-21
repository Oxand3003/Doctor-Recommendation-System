package utp.edu.pe.recomendaciones.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "valoraciones")
public class Valoracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cita_id", nullable = false, unique = true)
    private Cita cita;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Usuario paciente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @Column(nullable = false)
    private int puntajeGeneral;

    @Column()
    private Integer calidez;

    @Column()
    private Integer explicacion;

    @Column()
    private Integer puntualidad;

    @Column()
    private Integer instalaciones;

    @Column(length = 500)
    private String comentario;

    @Column(nullable = false)
    private LocalDateTime creadaEn;

    /**
     * Solo controla si la reseña se muestra en el perfil público. No altera la reseña ni el cálculo de
     * ratingPromedio/totalValoraciones. El default de la columna evita ocultar las reseñas existentes
     * cuando Hibernate agrega la columna a una tabla con datos.
     */
    @Column(name = "visible_publico", nullable = false, columnDefinition = "boolean default true")
    private boolean visiblePublico = true;

    public Valoracion() {
    }

    @PrePersist
    void prePersist() {
        if (creadaEn == null) creadaEn = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Cita getCita() {
        return cita;
    }

    public void setCita(Cita cita) {
        this.cita = cita;
    }

    public Usuario getPaciente() {
        return paciente;
    }

    public void setPaciente(Usuario paciente) {
        this.paciente = paciente;
    }

    public Medico getMedico() {
        return medico;
    }

    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    public int getPuntajeGeneral() {
        return puntajeGeneral;
    }

    public void setPuntajeGeneral(int puntajeGeneral) {
        this.puntajeGeneral = puntajeGeneral;
    }

    public Integer getCalidez() {
        return calidez;
    }

    public void setCalidez(Integer calidez) {
        this.calidez = calidez;
    }

    public Integer getExplicacion() {
        return explicacion;
    }

    public void setExplicacion(Integer explicacion) {
        this.explicacion = explicacion;
    }

    public Integer getPuntualidad() {
        return puntualidad;
    }

    public void setPuntualidad(Integer puntualidad) {
        this.puntualidad = puntualidad;
    }

    public Integer getInstalaciones() {
        return instalaciones;
    }

    public void setInstalaciones(Integer instalaciones) {
        this.instalaciones = instalaciones;
    }

    public String getComentario() {
        return comentario;
    }

    public void setComentario(String comentario) {
        this.comentario = comentario;
    }

    public boolean isVisiblePublico() {
        return visiblePublico;
    }

    public void setVisiblePublico(boolean visiblePublico) {
        this.visiblePublico = visiblePublico;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public void setCreadaEn(LocalDateTime creadaEn) {
        this.creadaEn = creadaEn;
    }

}
