package utp.edu.pe.recomendaciones.exception;

/**
 * La reserva choca con otra cita (el turno ya fue tomado o el paciente ya tiene una cita a esa hora).
 * Es una NegocioException, pero el controlador la responde como 409 para que el navegador sepa que debe
 * volver a cargar los turnos libres.
 */
public class ConflictoReservaException extends NegocioException {
    public ConflictoReservaException(String message) {
        super(message);
    }
}
