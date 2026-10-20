package utp.edu.pe.recomendaciones.model.enums;

/** Estado de una solicitud para desactivar una cuenta de administrador. */
public enum EstadoDesactivacionAdmin {
    /** Esperando votos de otros administradores. */
    PENDIENTE,
    /** Alcanzó los votos requeridos y la cuenta quedó suspendida. */
    EJECUTADA,
    /** Un administrador (distinto de la cuenta afectada) la rechazó. */
    RECHAZADA,
    /** La canceló quien la inició, o la cuenta dejó de estar activa. */
    CANCELADA
}
