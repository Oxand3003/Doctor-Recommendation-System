package utp.edu.pe.recomendaciones.dto;

import java.util.List;

/**
 * Datos de "Mi cuenta". Para pacientes, sus citas vienen ya separadas:
 *  - pendientes: citas PENDIENTE o CONFIRMADA que todavía no terminaron (la más próxima primero);
 *  - historial: el resto (ya pasadas, canceladas, completadas o a las que no asistió), la más reciente primero.
 */
public record AccountView(String nombre, String rol, String email, String dni, String telefono,
                          String estado, Long medicoId, boolean paciente, boolean admin,
                          List<CitaItem> pendientes, List<CitaItem> historial) {

    /** estadoClave se usa como clase CSS (pendiente, confirmada, cancelada, completada, no-asistio). */
    public record CitaItem(Long id, Long medicoId, String codigo, String medico, String especialidad, String fecha,
                           String hora, String modalidad, String lugar, String estado, String estadoClave,
                           String motivo, String motivoCancelacion) { }
}
