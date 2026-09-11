package utp.edu.pe.recomendaciones.exception;

/** Se lanza cuando no existe un médico que cumpla el criterio solicitado. */
public class DoctorNotFoundException extends RuntimeException {

  public DoctorNotFoundException(String message) {
    super(message);
  }
}
