package utp.edu.pe.recomendaciones.service;

import org.springframework.stereotype.Service;
import utp.edu.pe.recomendaciones.domain.Doctor;
import utp.edu.pe.recomendaciones.dto.RecomendacionDTO;
import utp.edu.pe.recomendaciones.exception.DoctorNotFoundException;
import utp.edu.pe.recomendaciones.repository.DoctorRepository;

import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.List;

/* LOGICA DE NEGOCIO */

@Service
public class RecomendacionService {

  private static final double PESO_RATING = 0.70;
  private static final double PESO_EXPERIENCIA = 0.30;
  private static final double RATING_MAXIMO = 5.0;
  private static final double EXPERIENCIA_TOPE = 20.0;

  private final DoctorRepository doctorRepository;

  public RecomendacionService(DoctorRepository doctorRepository) {
    this.doctorRepository = doctorRepository;
  }

  /**
   * Devuelve la lista de medicos recomendados para una especialidad,
   * ordenada del mejor puntaje al menor.
   *
   * @param especialidad nombre de la especialidad (ej: "Cardiologia")
   * @param limite       cantidad maxima de resultados (top N)
   */
  public List<RecomendacionDTO> recomendarPorEspecialidad(String especialidad, int limite) {
    if (especialidad == null || especialidad.isBlank()) {
      throw new IllegalArgumentException("La especialidad no puede estar vacia.");
    }
    if (limite <= 0) {
      throw new IllegalArgumentException("El limite debe ser mayor a cero.");
    }

    List<Doctor> disponibles = doctorRepository
        .findByEspecialidad_NombreIgnoreCaseAndDisponibleTrue(especialidad.trim());

    if (disponibles.isEmpty()) {
      throw new DoctorNotFoundException(
          "No se encontraron medicos disponibles para la especialidad: " + especialidad);
    }

    return disponibles.stream()
        .map(this::mapearConPuntaje)
        .sorted(Comparator.comparingDouble(RecomendacionDTO::getPuntajeRecomendacion).reversed())
        .limit(limite)
        .collect(Collectors.toList());
  }

  /** Convierte un Doctor en DTO y calcula su puntaje de recomendacion. */
  private RecomendacionDTO mapearConPuntaje(Doctor doctor) {
    double puntaje = calcularPuntaje(doctor);

    String establecimiento = doctor.getEstablecimiento() != null
        ? doctor.getEstablecimiento().getNombre()
        : "No asignado";

    return new RecomendacionDTO(
        doctor.getId(),
        doctor.getNombres() + " " + doctor.getApellidos(),
        doctor.getCmp(),
        doctor.getEspecialidad().getNombre(),
        doctor.getRating(),
        doctor.getAniosExperiencia(),
        establecimiento,
        puntaje);
  }

  /** Formula del puntaje ponderado, redondeado a 2 decimales. */
  private double calcularPuntaje(Doctor doctor) {
    double ratingNorm = doctor.getRating() / RATING_MAXIMO;
    double expNorm = Math.min(doctor.getAniosExperiencia(), EXPERIENCIA_TOPE) / EXPERIENCIA_TOPE;

    double puntaje = (ratingNorm * PESO_RATING + expNorm * PESO_EXPERIENCIA) * 100.0;
    return Math.round(puntaje * 100.0) / 100.0;
  }
}
