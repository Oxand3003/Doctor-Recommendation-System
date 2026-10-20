package utp.edu.pe.recomendaciones.dto;

import java.util.List;

/** Lo que necesita la plantilla medico/horarios.html. */
public record DisponibilidadView(List<DiaView> dias, List<ModalidadOpcion> modalidades,
                                 int franjasActivas, int franjasTotal, int maxFranjas,
                                 List<ReservaView> reservas) {

    /** Turno ya tomado por un paciente (PENDIENTE o CONFIRMADA) dentro de los próximos días. */
    public record ReservaView(Long citaId, String fecha, String hora, String paciente, String modalidad,
                              String estado, String estadoClave) { }

    public record DiaView(int numero, String nombre, List<FranjaView> franjas) { }

    public record FranjaView(Long id, int dia, String inicio, String fin, int duracionMin,
                             String modalidad, String modalidadLabel, boolean activo, int turnos) { }

    public record ModalidadOpcion(String valor, String label) { }
}
