package utp.edu.pe.recomendaciones.model.enums;

/** Para qué se emitió un código de seguridad (se guarda como texto en la base de datos). */
public enum PropositoCodigo {
    /** Cambiar la contraseña de inicio de sesión (desde el perfil, con sesión iniciada). */
    CAMBIO_PASSWORD,
    /** Cambiar el correo de inicio de sesión. */
    CAMBIO_EMAIL,
    /** Recuperar el acceso cuando se olvidó la contraseña (sin sesión iniciada). */
    RECUPERAR_PASSWORD,
    ADMIN_LOGIN,
    ADMIN_CREAR
}
