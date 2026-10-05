package utp.edu.pe.recomendaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import utp.edu.pe.recomendaciones.dto.CitaDTO;
import utp.edu.pe.recomendaciones.dto.CitaMedicoDTO;
import utp.edu.pe.recomendaciones.service.CitaService;

@RestController
@RequestMapping("/api/v1")
public class PortalUsuarioController {
  private final CitaService citaService;

  public PortalUsuarioController(CitaService citaService) {
    this.citaService = citaService;
  }

  @GetMapping("/client/me/appointments")
  @Operation(summary = "Lista las citas de la cuenta cliente autenticada")
  public ResponseEntity<List<CitaDTO>> citasCliente(Authentication authentication) {
    return ResponseEntity.ok(citaService.citasDelCliente(authentication.getName()));
  }

  @GetMapping("/doctor/me/appointments")
  @Operation(summary = "Lista las citas asignadas al médico autenticado")
  public ResponseEntity<List<CitaMedicoDTO>> citasMedico(Authentication authentication) {
    return ResponseEntity.ok(citaService.citasDelMedico(authentication.getName()));
  }
}
