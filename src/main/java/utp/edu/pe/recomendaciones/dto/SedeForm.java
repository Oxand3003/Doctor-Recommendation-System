package utp.edu.pe.recomendaciones.dto;

/** Alta/edición de una sede propia del médico. */
public record SedeForm(String nombre, String direccion, String referencia, Long distritoId) {
}
