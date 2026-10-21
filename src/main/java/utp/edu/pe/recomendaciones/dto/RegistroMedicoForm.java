package utp.edu.pe.recomendaciones.dto;

public record RegistroMedicoForm(String nombres, String apellidos, String dni, String telefono,
                                 String email, String password,
                                 String cmp, String rne, String tituloProfesional, String universidad,
                                 Integer anioEgreso, Integer aniosExperiencia,
                                 Long especialidadId, Long establecimientoId, String sustentoUrl) {
}
