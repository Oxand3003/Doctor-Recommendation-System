package utp.edu.pe.recomendaciones.model.enums;

/** Motivos que el visitante puede elegir en el formulario de contacto del inicio. */
public enum MotivoConsulta {
    ELIMINAR_CUENTA("Solicitar eliminar mi cuenta"),
    DENUNCIAR("Denunciar a un médico o un contenido"),
    PROBLEMA_CUENTA("Problemas con mi cuenta o mi acceso"),
    PROBLEMA_CITA("Problemas con una cita"),
    CORREGIR_DATOS("Corregir o actualizar mis datos"),
    SUGERENCIA("Sugerencia o comentario"),
    OTRO("Otro motivo");

    private final String etiqueta;

    MotivoConsulta(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
