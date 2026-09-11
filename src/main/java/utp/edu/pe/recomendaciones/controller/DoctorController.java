package utp.edu.pe.recomendaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import utp.edu.pe.recomendaciones.domain.Doctor;
import utp.edu.pe.recomendaciones.dto.RecomendacionDTO;
import utp.edu.pe.recomendaciones.service.DoctorService;
import utp.edu.pe.recomendaciones.service.RecomendacionService;

import java.util.List;

/* Endpoints para la gestión de doctores */

@RestController
@RequestMapping("/api/v1/doctors")
@CrossOrigin(origins = "*")
@Tag(name = "Doctores", description = "Consulta y recomendación de médicos")
public class DoctorController {

  private final DoctorService doctorService;
  private final RecomendacionService recomendacionService;

  public DoctorController(DoctorService doctorService, RecomendacionService recomendacionService) {
    this.doctorService = doctorService;
    this.recomendacionService = recomendacionService;
  }

  @Operation(summary = "Lista todos los medicos resgistrados")
  @GetMapping
  public ResponseEntity<List<Doctor>> listar() {
    return ResponseEntity.ok(doctorService.listarTodos());
  }

  @Operation(summary = "Obtiene un medico por su id")
  @GetMapping("/{id}")
  public ResponseEntity<Doctor> obtenerPorId(@PathVariable Long id) {
    if (id == null || id <= 0) {
      throw new IllegalArgumentException("El id debe ser mayor a cero.");
    }
    return ResponseEntity.ok(doctorService.buscarPorId(id));
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
}
