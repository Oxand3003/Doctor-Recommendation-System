package utp.edu.pe.recomendaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import utp.edu.pe.recomendaciones.dto.EstablecimientoDTO;
import utp.edu.pe.recomendaciones.dto.EspecialidadDTO;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;
import utp.edu.pe.recomendaciones.repository.EstablecimientoRepository;

@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "Catálogos", description = "Especialidades y establecimientos disponibles")
public class CatalogoController {
  private final EspecialidadRepository especialidadRepository;
  private final EstablecimientoRepository establecimientoRepository;

  public CatalogoController(
      EspecialidadRepository especialidadRepository,
      EstablecimientoRepository establecimientoRepository) {
    this.especialidadRepository = especialidadRepository;
    this.establecimientoRepository = establecimientoRepository;
  }

  @GetMapping("/specialties")
  @Transactional(readOnly = true)
  @Operation(summary = "Lista especialidades para el registro médico")
  public ResponseEntity<List<EspecialidadDTO>> especialidades() {
    return ResponseEntity.ok(especialidadRepository.findAll().stream()
        .map(item -> new EspecialidadDTO(item.getId(), item.getNombre()))
        .sorted(Comparator.comparing(EspecialidadDTO::nombre))
        .toList());
  }

  @GetMapping("/establishments")
  @Transactional(readOnly = true)
  @Operation(summary = "Lista establecimientos de atención")
  public ResponseEntity<List<EstablecimientoDTO>> establecimientos() {
    return ResponseEntity.ok(establecimientoRepository.findAll().stream()
        .map(item -> new EstablecimientoDTO(item.getId(), item.getNombre(), item.getDireccion(), item.getDistrito()))
        .sorted(Comparator.comparing(EstablecimientoDTO::nombre))
        .toList());
  }
}
