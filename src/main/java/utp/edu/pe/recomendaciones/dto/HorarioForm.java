package utp.edu.pe.recomendaciones.dto;

import java.util.List;

/**
 * Alta/edición de una franja. dias: 1 = lunes … 7 = domingo (al crear puede haber varios, al editar uno).
 * horaInicio/horaFin llegan como "HH:mm"; modalidad = nombre del enum ModalidadAtencion.
 */
public record HorarioForm(List<Integer> dias, String horaInicio, String horaFin, Integer duracionMin,
                          String modalidad, boolean activo) {
}
