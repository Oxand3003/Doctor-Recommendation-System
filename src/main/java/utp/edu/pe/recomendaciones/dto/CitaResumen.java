package utp.edu.pe.recomendaciones.dto;

/** Una cita en una lista o casilla del calendario (solo lectura). estadoClave sirve de clase CSS. */
public record CitaResumen(Long id, String paciente, String fecha, String hora, String horaFin,
                          String modalidad, String estado, String estadoClave) {
}
