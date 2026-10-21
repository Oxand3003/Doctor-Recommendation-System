package utp.edu.pe.recomendaciones.dto;

import java.util.List;

import utp.edu.pe.recomendaciones.dto.DoctorProfileView.CriterioView;

/** Lo que necesita la plantilla medico/resenas.html. */
public record ResenasView(String promedio, String estrellas, int total, int visibles, int ocultas,
                          List<CriterioView> desglose, List<ResenaItem> items, String filtro) {

    public record ResenaItem(Long id, String iniciales, String autor, String fecha, String modalidad,
                             int puntaje, String estrellas, String comentario, boolean visible,
                             List<Criterio> criterios) { }

    public record Criterio(String nombre, int valor) { }
}
