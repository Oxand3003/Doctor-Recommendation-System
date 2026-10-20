package utp.edu.pe.recomendaciones.dto;

import java.util.List;

/** Datos de la pantalla "Gestión de administradores" (ya formateados para mostrar). */
public record AdminGestionView(int activos, int suspendidos, int votosRequeridos, boolean puedeIniciar,
                               List<AdminFila> admins, List<SolicitudFila> pendientes, List<SolicitudFila> historial) {

    public record AdminFila(Long id, String email, String estado, String creadoEn, String ultimoAcceso,
                            boolean esYo, boolean conSolicitud, boolean puedeSolicitar) { }

    public record SolicitudFila(Long id, String objetivoEmail, String iniciadorEmail, String motivo, String estado,
                                String creadoEn, String resueltoEn, int votos, int votosRequeridos,
                                List<String> votantes, boolean yoVote, boolean puedeAprobar,
                                boolean puedeRechazar, boolean puedeCancelar) { }
}
