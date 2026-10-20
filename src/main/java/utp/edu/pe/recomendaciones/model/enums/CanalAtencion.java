package utp.edu.pe.recomendaciones.model.enums;

/** Medio por el que el administrador contactó al usuario. */
public enum CanalAtencion {
    WHATSAPP("WhatsApp"), CORREO("Correo"), LLAMADA("Llamada"), OTRO("Otro");

    private final String etiqueta;

    CanalAtencion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
