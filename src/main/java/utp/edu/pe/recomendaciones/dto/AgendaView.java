package utp.edu.pe.recomendaciones.dto;

import java.time.LocalDate;
import java.util.List;

/** Calendario de agenda por día, semana o mes. En la vista de mes, dias trae semanas completas (lun-dom). */
public record AgendaView(String vista, String titulo, LocalDate fecha, LocalDate anterior, LocalDate siguiente,
                         LocalDate hoy, int totalCitas, List<Dia> dias) {

    public record Dia(LocalDate fecha, String nombre, int numero, boolean esHoy, boolean delMes,
                      List<CitaResumen> citas) {
    }
}
