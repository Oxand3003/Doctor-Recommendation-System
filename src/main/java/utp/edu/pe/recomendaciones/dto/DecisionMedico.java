package utp.edu.pe.recomendaciones.dto;

/**
 * Resultado de que un administrador apruebe o rechace una solicitud de registro médico: lo mínimo que
 * hace falta para avisar por correo al solicitante una vez confirmada la decisión en la base de datos.
 * Solo existe cuando la decisión se guardó (una solicitud ya revisada nunca produce una).
 */
public record DecisionMedico(boolean aprobada, String email, String nombre, String motivo) { }
