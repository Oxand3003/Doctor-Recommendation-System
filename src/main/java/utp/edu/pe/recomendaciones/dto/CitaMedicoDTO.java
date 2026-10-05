package utp.edu.pe.recomendaciones.dto;

import java.time.LocalDateTime;

public record CitaMedicoDTO(
    Long id,
    String pacienteNombre,
    String pacienteTelefono,
    String motivo,
    LocalDateTime fechaHora,
    String modalidad,
    String estado) {
}
