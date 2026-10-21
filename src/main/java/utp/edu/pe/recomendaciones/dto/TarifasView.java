package utp.edu.pe.recomendaciones.dto;

/** Lo que necesita la plantilla medico/tarifas.html. */
public record TarifasView(TarifasForm datos, String teleEfectiva, boolean teleHereda, int maxCosto) {

    /** Misma vista con otros valores en el formulario (p. ej. tras un error de validación). */
    public TarifasView conDatos(TarifasForm otros) {
        return new TarifasView(otros, teleEfectiva, teleHereda, maxCosto);
    }
}
