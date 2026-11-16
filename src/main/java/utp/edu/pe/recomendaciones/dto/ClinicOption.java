package utp.edu.pe.recomendaciones.dto;

/** Sede del buscador con el distrito donde está, para que al elegir un distrito solo se ofrezcan sus sedes. */
public record ClinicOption(String name, String district) {
}
