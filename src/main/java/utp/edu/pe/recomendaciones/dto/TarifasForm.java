package utp.edu.pe.recomendaciones.dto;

/**
 * Tarifas del médico. Llegan como texto (acepta coma o punto decimal) y el servicio las valida.
 * costoConsulta = consulta presencial (y precio base); costoTeleconsulta vacío = igual al presencial;
 * costoPorHora = tarifa referencial por hora.
 */
public record TarifasForm(String costoConsulta, String costoTeleconsulta, String costoPorHora)
        implements java.io.Serializable {
}
