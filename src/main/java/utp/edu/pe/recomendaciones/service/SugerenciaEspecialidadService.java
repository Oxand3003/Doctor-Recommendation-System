package utp.edu.pe.recomendaciones.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import utp.edu.pe.recomendaciones.domain.Especialidad;
import utp.edu.pe.recomendaciones.dto.SugerenciaDTO;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;

/**
 * Motor de reglas que sugiere una especialidad a partir de un síntoma en texto
 * libre (o de una zona del cuerpo elegida en el maniquí). No usa ningún
 * servicio externo: compara el texto contra un diccionario de palabras clave
 * por especialidad y puntúa por cantidad de coincidencias.
 */
@Service
public class SugerenciaEspecialidadService {

  private static final String ESPECIALIDAD_POR_DEFECTO = "Medicina General";
  private static final double CONFIANZA_POR_DEFECTO = 0.3;
  private static final int COINCIDENCIAS_PARA_CONFIANZA_MAXIMA = 3;

  private static final Map<String, List<String>> PALABRAS_CLAVE = construirDiccionario();

  private final EspecialidadRepository especialidadRepository;

  public SugerenciaEspecialidadService(EspecialidadRepository especialidadRepository) {
    this.especialidadRepository = especialidadRepository;
  }

  /** Analiza el texto y devuelve la especialidad más probable junto con alternativas. */
  public SugerenciaDTO sugerir(String sintoma) {
    if (sintoma == null || sintoma.isBlank()) {
      throw new IllegalArgumentException("Describe el sintoma o selecciona una zona del cuerpo.");
    }

    String normalizado = normalizar(sintoma);
    Map<String, List<String>> coincidenciasPorEspecialidad = new LinkedHashMap<>();

    for (Map.Entry<String, List<String>> entrada : PALABRAS_CLAVE.entrySet()) {
      List<String> encontradas = new ArrayList<>();
      for (String palabraClave : entrada.getValue()) {
        if (normalizado.contains(palabraClave)) {
          encontradas.add(palabraClave);
        }
      }
      if (!encontradas.isEmpty()) {
        coincidenciasPorEspecialidad.put(entrada.getKey(), encontradas);
      }
    }

    List<String> ranking = new ArrayList<>(coincidenciasPorEspecialidad.keySet());
    ranking.sort((a, b) -> coincidenciasPorEspecialidad.get(b).size() - coincidenciasPorEspecialidad.get(a).size());

    if (ranking.isEmpty()) {
      return new SugerenciaDTO(
          idDeEspecialidad(ESPECIALIDAD_POR_DEFECTO).orElse(null),
          ESPECIALIDAD_POR_DEFECTO,
          CONFIANZA_POR_DEFECTO,
          List.of(),
          List.of());
    }

    String principal = ranking.get(0);
    List<SugerenciaDTO> alternativas = new ArrayList<>();
    for (int i = 1; i < ranking.size() && i <= 2; i++) {
      String nombre = ranking.get(i);
      alternativas.add(new SugerenciaDTO(
          idDeEspecialidad(nombre).orElse(null),
          nombre,
          calcularConfianza(coincidenciasPorEspecialidad.get(nombre).size()),
          coincidenciasPorEspecialidad.get(nombre),
          List.of()));
    }

    return new SugerenciaDTO(
        idDeEspecialidad(principal).orElse(null),
        principal,
        calcularConfianza(coincidenciasPorEspecialidad.get(principal).size()),
        coincidenciasPorEspecialidad.get(principal),
        alternativas);
  }

  private double calcularConfianza(int cantidadCoincidencias) {
    double confianza = (double) cantidadCoincidencias / COINCIDENCIAS_PARA_CONFIANZA_MAXIMA;
    return Math.round(Math.min(confianza, 1.0) * 100.0) / 100.0;
  }

  private Optional<Long> idDeEspecialidad(String nombre) {
    return especialidadRepository.findAll().stream()
        .filter(especialidad -> especialidad.getNombre().equalsIgnoreCase(nombre))
        .map(Especialidad::getId)
        .findFirst();
  }

  /** Quita tildes y pasa a minúsculas para que la comparación no dependa de acentos. */
  private String normalizar(String texto) {
    String sinAcentos = Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "");
    return sinAcentos;
  }

  private static Map<String, List<String>> construirDiccionario() {
    Map<String, List<String>> diccionario = new LinkedHashMap<>();

    diccionario.put("Cardiología", List.of(
        "pecho", "corazon", "palpitacion", "palpitaciones", "presion alta", "presion arterial",
        "taquicardia", "dolor toracico", "falta de aire", "ahogo", "arritmia"));

    diccionario.put("Neurología", List.of(
        "cabeza", "migrana", "jaqueca", "mareo", "convulsion", "hormigueo", "memoria",
        "cerebro", "vertigo", "desmayo", "temblor"));

    diccionario.put("Odontología", List.of(
        "diente", "dientes", "muela", "muelas", "encia", "encias", "boca", "mandibula",
        "caries", "dolor de diente"));

    diccionario.put("Dermatología", List.of(
        "piel", "grano", "granos", "mancha", "manchas", "picazon", "comezon", "alergia",
        "acne", "cabello", "uñas", "unas", "erupcion", "sarpullido"));

    diccionario.put("Oftalmología", List.of(
        "ojo", "ojos", "vista", "vision", "ardor en los ojos", "lagrimeo", "borrosa",
        "enrojecimiento ocular"));

    diccionario.put("Traumatología", List.of(
        "hueso", "huesos", "articulacion", "espalda", "rodilla", "hombro", "fractura",
        "esguince", "tobillo", "brazo", "pierna", "cadera", "cuello", "columna"));

    diccionario.put("Pediatría", List.of(
        "niño", "nino", "niña", "nina", "bebe", "lactante", "vacuna", "recien nacido"));

    diccionario.put("Medicina General", List.of(
        "fiebre", "cansancio", "malestar general", "chequeo", "gripe", "resfriado",
        "control", "revision general", "abdomen", "estomago", "barriga", "digestion",
        "nauseas", "vomito", "diarrea"));

    return diccionario;
  }
}
