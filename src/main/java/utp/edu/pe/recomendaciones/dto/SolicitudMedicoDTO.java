package utp.edu.pe.recomendaciones.dto;

public record SolicitudMedicoDTO(
    Long doctorId,
    Long usuarioId,
    String nombres,
    String apellidos,
    String dni,
    String email,
    String telefono,
    String cmp,
    String rne,
    String tituloProfesional,
    String universidad,
    Integer anioEgreso,
    Integer aniosExperiencia,
    String especialidad,
    String establecimiento,
    String sustentoUrl,
    String estadoVerificacion) {
}
