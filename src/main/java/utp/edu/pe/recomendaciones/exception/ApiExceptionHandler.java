package utp.edu.pe.recomendaciones.exception;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Unico manejador global de errores del API.
 * Todas las respuestas de error comparten la misma forma: {"error", "message"}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(DoctorNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleDoctorNotFound(DoctorNotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<Map<String, String>> handleBadRequest(Exception exception) {
    return response(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  /** Errores de validacion de los DTO anotados con @Valid. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
    String detalle = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage())
        .collect(Collectors.joining(" | "));
    return response(HttpStatus.BAD_REQUEST,
        detalle.isBlank() ? "Datos de entrada invalidos." : detalle);
  }

  /** Choque de restricciones en base de datos (por ejemplo, un unico repetido). */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException exception) {
    return response(HttpStatus.CONFLICT,
        "La operacion viola una restriccion de la base de datos.");
  }

  /** Red de seguridad: cualquier error no controlado. */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleUnexpected(Exception exception) {
    return response(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor.");
  }

  private ResponseEntity<Map<String, String>> response(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of(
        "error", status.getReasonPhrase(),
        "message", message == null ? "Solicitud invalida." : message));
  }
}
