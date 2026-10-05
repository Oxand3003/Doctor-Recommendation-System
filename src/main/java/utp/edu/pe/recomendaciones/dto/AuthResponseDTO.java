package utp.edu.pe.recomendaciones.dto;

public record AuthResponseDTO(
    Long id,
    String nombres,
    String apellidos,
    String email,
    String dni,
    String telefono,
    String rol,
    String estado,
    String mensaje,
    Long doctorId) {
}
