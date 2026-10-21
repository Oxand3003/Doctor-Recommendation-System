package utp.edu.pe.recomendaciones.dto;

/** Resultado de reservar una cita: lo que el navegador muestra en la ventana de éxito. */
public record ReservaCreada(Long id, String codigo, String medico, String fecha, String hora,
                            String modalidad, String estado) {
}
