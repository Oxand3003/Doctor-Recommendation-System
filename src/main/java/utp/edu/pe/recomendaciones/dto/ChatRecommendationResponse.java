package utp.edu.pe.recomendaciones.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * @param sort        orden vigente: "rating" o "price".
 * @param stage       punto del flujo (MENU, SINTOMAS, ELEGIR, LIBRE).
 * @param options     botones de respuesta rápida que el chat muestra bajo el último mensaje.
 * @param suggestions especialidades que el asistente orienta según los síntomas (la primera ya quedó elegida).
 * @param urgent      true si lo descrito podría ser una urgencia (el mensaje ya avisa que acuda a emergencias).
 */
public record ChatRecommendationResponse(String reply, String specialty, String district,
                                         LocalDate date, LocalTime time, boolean complete, List<String> missing,
                                         List<String> specialties,
                                         List<DoctorRecommendationView> doctors,
                                         String sort, String stage, List<Option> options,
                                         List<Suggestion> suggestions, boolean urgent) {

    /** action: acción rápida (null = enviar value como texto); value: especialidad, orden o texto según la acción. */
    public record Option(String label, String action, String value) { }

    public record Suggestion(String specialty, String reason) { }

    /** availableTimes: turnos libres reales (desde la hora pedida) cuando ya hay fecha; vacío si aún no. */
    public record DoctorRecommendationView(Long id, String name, String specialty, double rating,
                                           int reviews, int experienceYears, String clinic, String district,
                                           boolean available, String availability, List<String> availableTimes,
                                           Double price, String priceText) { }
}
