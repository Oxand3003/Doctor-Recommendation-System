package utp.edu.pe.recomendaciones.dto;

/**
 * Quién está viendo el perfil de un médico, para la ventana de reserva.
 * estado: PACIENTE (puede reservar), ANONIMO (sin sesión) u OTRO (médico o administrador: no reserva).
 * Nombre, DNI y teléfono salen de la cuenta del paciente; vacíos si no es paciente.
 */
public record PerfilReserva(String estado, String nombre, String dni, String telefono) {

    public static final PerfilReserva ANONIMO = new PerfilReserva("ANONIMO", "", "", "");

    public boolean paciente() {
        return "PACIENTE".equals(estado);
    }
}
