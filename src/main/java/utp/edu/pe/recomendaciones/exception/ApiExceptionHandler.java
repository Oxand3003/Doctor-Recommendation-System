package utp.edu.pe.recomendaciones.exception;


import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Convierte errores esperados del API en respuestas HTTP consistentes. */
@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(DoctorNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleDoctorNotFound(DoctorNotFoundException exception) {
    return response(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<Map<String, String>> handleBadRequest(Exception exception) {
    return response(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  private ResponseEntity<Map<String, String>> response(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of(
        "error", status.getReasonPhrase(),
        "message", message == null ? "Solicitud invalida." : message));
  }
}