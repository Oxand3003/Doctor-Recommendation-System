package utp.edu.pe.recomendaciones.dto;

import java.time.LocalDateTime;

/** Confirmación de una reserva, sin exponer los datos privados del paciente. */
public record CitaDTO(
    Long id,
    Long doctorId,
    String doctorNombre,
    LocalDateTime fechaHora,
    String modalidad,
    String estado) {
}
