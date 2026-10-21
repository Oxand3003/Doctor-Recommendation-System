package utp.edu.pe.recomendaciones.dto;

/** Un turno de la agenda: "09:40", disponible o no. */
public record SlotView(String hora, boolean disponible) {
}

