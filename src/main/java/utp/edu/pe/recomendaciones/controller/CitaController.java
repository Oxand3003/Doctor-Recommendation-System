package utp.edu.pe.recomendaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import utp.edu.pe.recomendaciones.dto.CitaDTO;
import utp.edu.pe.recomendaciones.dto.CitaRequestDTO;
import utp.edu.pe.recomendaciones.service.CitaService;

@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "Citas", description = "Registro de reservas de atención")
public class CitaController {
  private final CitaService citaService;

  public CitaController(CitaService citaService) {
    this.citaService = citaService;
  }

  @Operation(summary = "Registra una cita y confirma el horario")
  @PostMapping
  public ResponseEntity<CitaDTO> reservar(
      @Valid @RequestBody CitaRequestDTO request,
      Authentication authentication) {
    CitaDTO cita = citaService.reservar(request, authentication.getName());
    URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(cita.id())
        .toUri();
    return ResponseEntity.created(ubicacion).body(cita);
  }

  @Operation(summary = "Consulta el estado de una cita registrada")
  @GetMapping("/{id}")
  public ResponseEntity<CitaDTO> buscarPorId(@PathVariable Long id, Authentication authentication) {
    if (id == null || id <= 0) {
      throw new IllegalArgumentException("El id debe ser mayor a cero.");
    }
    boolean administrador = authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch("ROLE_ADMINISTRADOR"::equals);
    return ResponseEntity.ok(citaService.buscarPorId(id, authentication.getName(), administrador));
  }

  @Operation(summary = "Consulta los horarios ya reservados para un especialista y fecha")
  @GetMapping("/availability")
  public ResponseEntity<List<String>> horariosOcupados(
      @RequestParam Long doctorId,
      @RequestParam LocalDate fecha) {
    if (doctorId <= 0) {
      throw new IllegalArgumentException("El id del doctor debe ser mayor a cero.");
    }
    return ResponseEntity.ok(citaService.horariosOcupados(doctorId, fecha));
  }
}
