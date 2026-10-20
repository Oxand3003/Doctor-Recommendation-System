package utp.edu.pe.recomendaciones.dto;

/** Fila del panel de administración (JSON). Los nombres de campo deben coincidir con admin.js. */
public record ConsultaView(Long id, String nombres, String correo, String telefono, String whatsappUrl,
                           String motivo, String motivoCodigo, String mensaje, String fecha, String estado,
                           boolean conCuenta, String fechaAtencion, String canal, String atendidaPor,
                           String respuesta) {
}
