package utp.edu.pe.recomendaciones.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

/**
 * @param action opciones rápidas del chat: MENU, SINTOMAS, ESPECIALIDAD, PRECIOS, CALIFICADOS, ELEGIR (la
 *               especialidad va en {@code specialty}; vacío = todas) y ORDEN (el criterio va en {@code sort}).
 *               Nulo = el paciente escribió un texto libre.
 * @param sort   "rating" (mejor calificados) o "price" (menor precio); nulo = el que ya tenía la conversación.
 * @param applyMessageFilters true cuando el paciente habló (texto u opción); false al refrescar filtros.
 */
public record ChatRecommendationRequest(
        @NotBlank @Size(max = 500) String message,
        @Size(max = 100) String specialty,
        @Size(max = 100) String district,
        LocalDate date,
        LocalTime time,
        boolean applyMessageFilters,
        @Size(max = 10) List<@Valid ChatTurn> history,
        @Size(max = 20) String action,
        @Size(max = 10) String sort) {

    public record ChatTurn(@NotBlank @Size(max = 10) String role,
                           @NotBlank @Size(max = 500) String content) { }
}
