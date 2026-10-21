package utp.edu.pe.recomendaciones.dto;

import java.time.LocalDate;

/** Detalle de una cita para el médico. Los datos de contacto son los que el paciente registró. */
public record CitaDetalleView(Long id, String paciente, String telefono, String email, String fecha,
                              LocalDate fechaIso, String hora, String horaFin, int duracionMin,
                              String modalidad, boolean presencial, String sedeNombre, String sedeDireccion,
                              String sedeReferencia, String estado, String estadoClave, String motivo,
                              String motivoCancelacion, String precio, String reservadaEl,
                              String whatsappUrl, boolean eliminable) {
}
