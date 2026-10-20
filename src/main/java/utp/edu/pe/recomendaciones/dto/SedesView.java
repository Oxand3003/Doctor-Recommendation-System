package utp.edu.pe.recomendaciones.dto;

import java.util.List;

/** Lo que necesita la plantilla medico/sedes.html. */
public record SedesView(List<SedeItem> propias, Long principalId, String principalNombre,
                        List<OpcionView> opcionesPrincipal, List<OpcionView> distritos, int maxSedes) {

    public record SedeItem(Long id, String nombre, String direccion, String referencia,
                           Long distritoId, String distrito, boolean principal) { }
}
