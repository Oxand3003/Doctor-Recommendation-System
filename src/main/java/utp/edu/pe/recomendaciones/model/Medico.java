package utp.edu.pe.recomendaciones.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import utp.edu.pe.recomendaciones.model.enums.Modalidad;

@Entity
@Table(name = "medicos")
public class Medico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(nullable = false, unique = true, length = 20)
    private String cmp;

    @Column(length = 20)
    private String rne;

    @Column(nullable = false)
    private int aniosExperiencia;

    @Column(length = 150)
    private String tituloProfesional;

    @Column(length = 180)
    private String universidad;

    @Column()
    private Integer anioEgreso;

    @Column(length = 2000)
    private String biografia;

    @Column(name = "mensaje_predeterminado_chat", length = 1000)
    private String mensajePredeterminadoChat;

    /** Precio de la consulta PRESENCIAL (y precio base: es el que ya usaba el perfil público y Cita.precio). */
    @Column(precision = 10, scale = 2)
    private BigDecimal costoConsulta;

    /** Precio de la TELECONSULTA. null = no definido, se cobra el costoConsulta. */
    @Column(precision = 10, scale = 2)
    private BigDecimal costoTeleconsulta;

    /** Tarifa referencial por hora de atención. null = no definida. Solo informativa. */
    @Column(precision = 10, scale = 2)
    private BigDecimal costoPorHora;

    @Column(nullable = false)
    private double ratingPromedio;

    @Column(nullable = false)
    private int totalValoraciones;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "especialidad_id", nullable = false)
    private Especialidad especialidad;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "establecimiento_id")
    private Establecimiento establecimiento;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;

    public Medico() {
    }

    @PrePersist
    void prePersist() {
        if (creadoEn == null) creadoEn = LocalDateTime.now();
        actualizadoEn = creadoEn;
    }

    @PreUpdate
    void preUpdate() {
        actualizadoEn = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public String getCmp() {
        return cmp;
    }

    public void setCmp(String cmp) {
        this.cmp = cmp;
    }

    public String getRne() {
        return rne;
    }

    public void setRne(String rne) {
        this.rne = rne;
    }

    public int getAniosExperiencia() {
        return aniosExperiencia;
    }

    public void setAniosExperiencia(int aniosExperiencia) {
        this.aniosExperiencia = aniosExperiencia;
    }

    public String getTituloProfesional() {
        return tituloProfesional;
    }

    public void setTituloProfesional(String tituloProfesional) {
        this.tituloProfesional = tituloProfesional;
    }

    public String getUniversidad() {
        return universidad;
    }

    public void setUniversidad(String universidad) {
        this.universidad = universidad;
    }

    public Integer getAnioEgreso() {
        return anioEgreso;
    }

    public void setAnioEgreso(Integer anioEgreso) {
        this.anioEgreso = anioEgreso;
    }

    public String getBiografia() {
        return biografia;
    }

    public void setBiografia(String biografia) {
        this.biografia = biografia;
    }

    public String getMensajePredeterminadoChat() {
        return mensajePredeterminadoChat;
    }

    public void setMensajePredeterminadoChat(String mensajePredeterminadoChat) {
        this.mensajePredeterminadoChat = mensajePredeterminadoChat;
    }

    public BigDecimal getCostoConsulta() {
        return costoConsulta;
    }

    public void setCostoConsulta(BigDecimal costoConsulta) {
        this.costoConsulta = costoConsulta;
    }

    public BigDecimal getCostoTeleconsulta() {
        return costoTeleconsulta;
    }

    public void setCostoTeleconsulta(BigDecimal costoTeleconsulta) {
        this.costoTeleconsulta = costoTeleconsulta;
    }

    public BigDecimal getCostoPorHora() {
        return costoPorHora;
    }

    public void setCostoPorHora(BigDecimal costoPorHora) {
        this.costoPorHora = costoPorHora;
    }

    /**
     * Precio que se debe cobrar por una cita en la modalidad indicada (úsalo para Cita.precio).
     * Teleconsulta sin precio propio hereda costoConsulta, así que el resultado puede ser null solo
     * si el médico no definió ningún precio.
     */
    public BigDecimal precioPara(Modalidad modalidad) {
        if (modalidad == Modalidad.TELECONSULTA && costoTeleconsulta != null) {
            return costoTeleconsulta;
        }
        return costoConsulta;
    }

    public double getRatingPromedio() {
        return ratingPromedio;
    }

    public void setRatingPromedio(double ratingPromedio) {
        this.ratingPromedio = ratingPromedio;
    }

    public int getTotalValoraciones() {
        return totalValoraciones;
    }

    public void setTotalValoraciones(int totalValoraciones) {
        this.totalValoraciones = totalValoraciones;
    }

    public Especialidad getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(Especialidad especialidad) {
        this.especialidad = especialidad;
    }

    public Establecimiento getEstablecimiento() {
        return establecimiento;
    }

    public void setEstablecimiento(Establecimiento establecimiento) {
        this.establecimiento = establecimiento;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }

    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(LocalDateTime actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }

}
