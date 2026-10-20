package utp.edu.pe.recomendaciones.dto;

/** Alta/edición de una formación: tipo = nombre del enum TipoFormacion. */
public record FormacionForm(String tipo, String titulo, String institucion, Integer anio) {
}
