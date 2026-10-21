package utp.edu.pe.recomendaciones.model;

import jakarta.persistence.*;
import utp.edu.pe.recomendaciones.model.enums.TipoFormacion;

@Entity
@Table(name = "formaciones_medico")
public class FormacionMedico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medico_id", nullable = false)
    private Medico medico;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoFormacion tipo;

    @Column(nullable = false, length = 200)
    private String titulo;

    @Column(nullable = false, length = 200)
    private String institucion;

    @Column()
    private Integer anio;

    public FormacionMedico() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Medico getMedico() {
        return medico;
    }

    public void setMedico(Medico medico) {
        this.medico = medico;
    }

    public TipoFormacion getTipo() {
        return tipo;
    }

    public void setTipo(TipoFormacion tipo) {
        this.tipo = tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getInstitucion() {
        return institucion;
    }

    public void setInstitucion(String institucion) {
        this.institucion = institucion;
    }

    public Integer getAnio() {
        return anio;
    }

    public void setAnio(Integer anio) {
        this.anio = anio;
    }

}
