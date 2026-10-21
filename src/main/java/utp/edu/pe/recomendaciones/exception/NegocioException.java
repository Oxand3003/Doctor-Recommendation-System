package utp.edu.pe.recomendaciones.exception;

/** Error de regla de negocio con un mensaje apto para mostrar al usuario. */
public class NegocioException extends RuntimeException {
    public NegocioException(String message) {
        super(message);
    }
}
