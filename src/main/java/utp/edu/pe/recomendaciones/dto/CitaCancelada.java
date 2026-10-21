package utp.edu.pe.recomendaciones.dto;

/**
 * Datos de una cita que el médico eliminó (cancelación), listos para avisar al paciente por correo.
 * fechaIso sirve para volver a la agenda del día; motivo es opcional (texto del médico).
 */
public record CitaCancelada(Long id, String codigo, String pacienteNombre, String pacienteEmail, String medico,
                            String fecha, String hora, String modalidad, String motivo,
                            java.time.LocalDate fechaIso) {
}
