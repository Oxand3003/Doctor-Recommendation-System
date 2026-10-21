package utp.edu.pe.recomendaciones.dto;

/** Datos crudos del formulario de contacto (se validan en ConsultaService). */
public record ConsultaForm(String nombres, String correo, String telefono, String motivo, String mensaje) {
}
