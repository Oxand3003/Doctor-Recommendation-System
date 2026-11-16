package utp.edu.pe.recomendaciones.dto;

/**
 * Solicitud de registro de un médico.
 * {@code especialidad} y {@code establecimiento} llegan como texto: el médico los escribe o los elige de la lista
 * de sugerencias. {@code sustento} es el archivo (PDF o imagen) con su constancia CMP, RNE o título.
 */
public record RegistroMedicoForm(String nombres, String apellidos, String dni, String telefono,
                                 String email, String password, String passwordConfirmacion,
                                 String cmp, String rne, String tituloProfesional, String universidad,
                                 Integer anioEgreso, Integer aniosExperiencia,
                                 String especialidad, String establecimiento, ArchivoSustento sustento) {

    /** Archivo subido con el formulario; {@code tipoDeclarado} es lo que dijo el navegador (no se confía en él). */
    public record ArchivoSustento(String nombre, String tipoDeclarado, byte[] datos) { }
}
