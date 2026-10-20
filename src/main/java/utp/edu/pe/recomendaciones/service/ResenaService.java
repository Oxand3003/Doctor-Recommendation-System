package utp.edu.pe.recomendaciones.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.dto.ResenasView;
import utp.edu.pe.recomendaciones.dto.ResenasView.Criterio;
import utp.edu.pe.recomendaciones.dto.ResenasView.ResenaItem;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Cita;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.Valoracion;
import utp.edu.pe.recomendaciones.model.enums.Modalidad;
import utp.edu.pe.recomendaciones.repository.ValoracionRepository;

/**
 * Calificaciones y comentarios del médico. Es de SOLO LECTURA sobre la reseña: lo único que se puede
 * cambiar es Valoracion.visiblePublico (mostrar u ocultar en /doctor/{id}). No se edita ni se borra el
 * texto, y ratingPromedio / totalValoraciones de Medico no se recalculan: ocultar no es borrar.
 * Al médico se le muestra al paciente anonimizado igual que en el perfil público ("Carlos M.").
 */
@Service
public class ResenaService {

    private static final int MAX_ITEMS = 200;
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"));

    private final MedicoSesion sesion;
    private final ValoracionRepository valoraciones;

    public ResenaService(MedicoSesion sesion, ValoracionRepository valoraciones) {
        this.sesion = sesion;
        this.valoraciones = valoraciones;
    }

    /** filtro: "todas" (por defecto), "visibles" u "ocultas". */
    @Transactional(readOnly = true)
    public ResenasView ver(String email, String filtro) {
        Medico m = sesion.actual(email);
        List<Valoracion> todas = valoraciones.findByMedicoIdOrderByCreadaEnDesc(m.getId());
        String f = "visibles".equals(filtro) || "ocultas".equals(filtro) ? filtro : "todas";

        int visibles = (int) todas.stream().filter(Valoracion::isVisiblePublico).count();
        List<ResenaItem> items = todas.stream()
                .filter(v -> switch (f) {
                    case "visibles" -> v.isVisiblePublico();
                    case "ocultas" -> !v.isVisiblePublico();
                    default -> true;
                })
                .limit(MAX_ITEMS)
                .map(this::item)
                .toList();

        return new ResenasView(
                m.getTotalValoraciones() == 0 ? "Nuevo" : String.format(Locale.US, "%.1f", m.getRatingPromedio()),
                "★".repeat((int) Math.round(m.getRatingPromedio())),
                m.getTotalValoraciones(), visibles, todas.size() - visibles,
                DoctorProfileService.desglose(todas), items, f);
    }

    /** Cambia solo la visibilidad pública de una reseña de ESTE médico. */
    @Transactional
    public void cambiarVisibilidad(String email, Long id, boolean visible) {
        Medico m = sesion.actual(email);
        Valoracion v = valoraciones.findByIdAndMedicoId(id, m.getId())
                .orElseThrow(() -> new NegocioException("La reseña indicada no existe."));
        if (v.isVisiblePublico() != visible) {
            v.setVisiblePublico(visible);
            valoraciones.save(v);
        }
    }

    private ResenaItem item(Valoracion v) {
        Usuario p = v.getPaciente();
        Cita c = v.getCita();
        String autor = DoctorProfileService.primerToken(p.getNombres()) + " "
                + DoctorProfileService.primerToken(p.getApellidos()).charAt(0) + ".";
        String modalidad = c.getModalidad() == Modalidad.TELECONSULTA ? "Teleconsulta" : "Consulta presencial";
        List<Criterio> criterios = new java.util.ArrayList<>();
        if (v.getCalidez() != null) criterios.add(new Criterio("Calidez", v.getCalidez()));
        if (v.getExplicacion() != null) criterios.add(new Criterio("Explicación", v.getExplicacion()));
        if (v.getPuntualidad() != null) criterios.add(new Criterio("Puntualidad", v.getPuntualidad()));
        if (v.getInstalaciones() != null) criterios.add(new Criterio("Instalaciones", v.getInstalaciones()));
        String comentario = v.getComentario() == null || v.getComentario().isBlank() ? null : v.getComentario();
        return new ResenaItem(v.getId(), DoctorProfileService.iniciales(p), autor,
                c.getFechaHoraInicio().format(FECHA), modalidad, v.getPuntajeGeneral(),
                "★".repeat(v.getPuntajeGeneral()), comentario, v.isVisiblePublico(), criterios);
    }
}
