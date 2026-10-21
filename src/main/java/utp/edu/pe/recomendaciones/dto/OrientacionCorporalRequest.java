package utp.edu.pe.recomendaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pedido de orientación por zona corporal.
 *
 * @param sex       "mujer" u "hombre" (decide el maniquí y la especialidad de la zona pélvica).
 * @param symptoms  lo que el paciente describió (puede venir vacío si solo tocó una zona del maniquí).
 * @param zone      zona tocada en el maniquí (cabeza, ojos, pecho...); nulo = la IA deduce la zona de los síntomas.
 * @param specialty especialidad que el asistente ya eligió en el chat (opcional, ayuda a ubicar la zona).
 */
public record OrientacionCorporalRequest(
        @NotBlank @Size(max = 10) String sex,
        @Size(max = 500) String symptoms,
        @Size(max = 30) String zone,
        @Size(max = 100) String specialty) { }
