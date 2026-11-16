package utp.edu.pe.recomendaciones.dto;

/** Cuenta (médico o paciente) que un administrador acaba de suspender, con el motivo para avisarle por correo. */
public record SuspensionCuenta(String email, String nombre, String motivo) {
}
