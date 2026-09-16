package utp.edu.pe.recomendaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import utp.edu.pe.recomendaciones.domain.Doctor;
import utp.edu.pe.recomendaciones.dto.DoctorDTO;
import utp.edu.pe.recomendaciones.dto.DoctorRequestDTO;
import utp.edu.pe.recomendaciones.dto.RecomendacionDTO;
import utp.edu.pe.recomendaciones.service.DoctorService;
import utp.edu.pe.recomendaciones.service.RecomendacionService;

/* Endpoints para la gestion de doctores */

@RestController
@RequestMapping("/api/v1/doctors")
@Tag(name = "Doctores", description = "CRUD y recomendacion de medicos")
public class DoctorController {

  private final DoctorService doctorService;
  private final RecomendacionService recomendacionService;

  public DoctorController(DoctorService doctorService, RecomendacionService recomendacionService) {
    this.doctorService = doctorService;
    this.recomendacionService = recomendacionService;
  }

  @Operation(summary = "Lista todos los medicos registrados")
  @GetMapping
  public ResponseEntity<List<DoctorDTO>> listar() {
    List<DoctorDTO> doctores = doctorService.listarTodos().stream()
        .map(DoctorController::aDTO)
        .toList();
    return ResponseEntity.ok(doctores);
  }

  @Operation(summary = "Obtiene un medico por su id")
  @GetMapping("/{id}")
  public ResponseEntity<DoctorDTO> obtenerPorId(@PathVariable Long id) {
    if (id == null || id <= 0) {
      throw new IllegalArgumentException("El id debe ser mayor a cero.");
    }
    return ResponseEntity.ok(aDTO(doctorService.buscarPorId(id)));
  }

  @Operation(summary = "Registra un medico nuevo")
  @PostMapping
  public ResponseEntity<DoctorDTO> crear(@Valid @RequestBody DoctorRequestDTO request) {
    Doctor creado = doctorService.crear(request);
    URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(creado.getId())
        .toUri();
    return ResponseEntity.created(ubicacion).body(aDTO(creado));
  }

  @Operation(summary = "Actualiza los datos de un medico")
  @PutMapping("/{id}")
  public ResponseEntity<DoctorDTO> actualizar(
      @PathVariable Long id,
      @Valid @RequestBody DoctorRequestDTO request) {
    if (id == null || id <= 0) {
      throw new IllegalArgumentException("El id debe ser mayor a cero.");
    }
    return ResponseEntity.ok(aDTO(doctorService.actualizar(id, request)));
  }

  @Operation(summary = "Elimina un medico por su id")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> eliminar(@PathVariable Long id) {
    if (id == null || id <= 0) {
      throw new IllegalArgumentException("El id debe ser mayor a cero.");
    }
    doctorService.eliminar(id);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "Recomienda medicos por especialidad ordenados por puntaje")
  @GetMapping("/recomendar")
  public ResponseEntity<List<RecomendacionDTO>> recomendar(
      @RequestParam(name = "especialidad") String especialidad,
      @RequestParam(name = "limite", defaultValue = "5") int limite) {

    if (especialidad == null || especialidad.isBlank()) {
      throw new IllegalArgumentException("La especialidad no puede estar vacia.");
    }
    if (limite <= 0) {
      throw new IllegalArgumentException("El limite debe ser mayor a cero.");
    }

    List<RecomendacionDTO> recomendados = recomendacionService.recomendarPorEspecialidad(especialidad, limite);
    return ResponseEntity.ok(recomendados);
  }

  /** Convierte la entidad en el DTO que se expone al cliente. */
  private static DoctorDTO aDTO(Doctor doctor) {
    return new DoctorDTO(
        doctor.getId(),
        doctor.getNombres(),
        doctor.getApellidos(),
        doctor.getCmp(),
        doctor.getRating(),
        doctor.getAniosExperiencia(),
        doctor.getDisponible(),
        doctor.getEspecialidad() == null ? null : doctor.getEspecialidad().getId(),
        doctor.getEspecialidad() == null ? null : doctor.getEspecialidad().getNombre(),
        doctor.getEstablecimiento() == null ? null : doctor.getEstablecimiento().getId(),
        doctor.getEstablecimiento() == null ? null : doctor.getEstablecimiento().getNombre());
  }
}
