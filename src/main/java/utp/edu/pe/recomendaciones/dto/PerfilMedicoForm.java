package utp.edu.pe.recomendaciones.dto;

/**
 * Datos editables del perfil profesional público. El costo de consulta ya no se edita aquí:
 * vive en TarifasForm (/medico/tarifas) junto con el precio de teleconsulta y por hora.
 */
public record PerfilMedicoForm(String nombres, String apellidos, Long especialidadId, Long establecimientoId,
                               String tituloProfesional, String universidad, Integer anioEgreso,
                               Integer aniosExperiencia, String biografia, String mensajePredeterminadoChat)
        implements java.io.Serializable {

    /** Compatibilidad con los formularios de perfil anteriores a la configuración del chat. */
    public PerfilMedicoForm(String nombres, String apellidos, Long especialidadId, Long establecimientoId,
                            String tituloProfesional, String universidad, Integer anioEgreso,
                            Integer aniosExperiencia, String biografia) {
        this(nombres, apellidos, especialidadId, establecimientoId, tituloProfesional, universidad,
                anioEgreso, aniosExperiencia, biografia, null);
    }
}
