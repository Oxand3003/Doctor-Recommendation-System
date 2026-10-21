package utp.edu.pe.recomendaciones.dto;

import java.util.List;

/** Indicadores del médico calculados con sus citas y valoraciones reales. */
public record DashboardMedicoView(String nombre, long pacientesAtendidos, long citasProgramadas,
                                  long citasPendientes, String calificacion, String estrellas,
                                  int totalValoraciones, List<CitaResumen> proximas) {
}
