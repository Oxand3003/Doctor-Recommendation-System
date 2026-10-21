package utp.edu.pe.recomendaciones.dto;



import java.util.List;

/**
 * Orientación por zona corporal: qué zona se marca en el maniquí, qué especialidad se sugiere y por qué.
 * No es un diagnóstico médico.
 *
 * @param zone          id de la zona del maniquí (cabeza, ojos, oidos, boca, salud_mental, pecho, abdomen, espalda,
 *                      piel, hormonas, brazo, pelvis, rodilla, tobillo, general).
 * @param specialty     nombre EXACTO de la especialidad en la base; nulo si no hay médicos de esa especialidad.
 * @param aiGenerated   true si el detalle lo redactó la IA; false si salió de las reglas internas.
 * @param probableCauses posibles causas a descartar (solo con IA; son hipótesis, no un diagnóstico).
 */
public record OrientacionCorporalResponse(String sex, String sexLabel, String zone, String zoneLabel,
                                          String specialty, boolean urgent, boolean aiGenerated,
                                          String summary, String whyZone, String whySpecialty,
                                          List<String> probableCauses, List<String> selfCare,
                                          List<String> warningSigns, List<Alternative> alternatives,
                                          String disclaimer, String generatedAt) {

    public record Alternative(String specialty, String reason) { }
}

