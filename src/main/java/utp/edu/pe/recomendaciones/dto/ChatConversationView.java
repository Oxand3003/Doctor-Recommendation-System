package utp.edu.pe.recomendaciones.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Conversación guardada del asistente de IA: mensajes y lo que ya entendió (para retomarla). */
public record ChatConversationView(List<Turn> messages, State state) {

    public record Turn(String role, String content) { }

    public record State(String specialty, String district, LocalDate date, LocalTime time,
                        String sort, String stage, String purpose) {
        public static State inicial() {
            return new State(null, null, null, null, "rating", "MENU", null);
        }
    }
}
