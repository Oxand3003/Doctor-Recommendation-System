package utp.edu.pe.recomendaciones.dto;

/**
 * Estado de la sección "Seguridad de la cuenta" del perfil privado del médico (medico/perfil.html).
 * Nunca contiene códigos ni contraseñas: solo si hay un código esperando y a qué correo se envió.
 */
public record CredencialesView(String correoActualEnmascarado,
                               boolean passwordCodigoPendiente,
                               boolean emailCodigoPendiente,
                               String nuevoEmailPendiente,
                               int vigenciaMinutos) { }
