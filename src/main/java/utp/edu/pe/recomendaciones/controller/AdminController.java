package utp.edu.pe.recomendaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import utp.edu.pe.recomendaciones.dto.SolicitudMedicoDTO;
import utp.edu.pe.recomendaciones.dto.RechazoSolicitudDTO;
import utp.edu.pe.recomendaciones.service.AdminService;

@RestController
@RequestMapping("/api/v1/admin/doctor-applications")
@Tag(name = "Administración", description = "Revisión de solicitudes de médicos")
public class AdminController {
  private final AdminService adminService;

  public AdminController(AdminService adminService) {
    this.adminService = adminService;
  }

  @Operation(summary = "Lista solicitudes de médicos que esperan verificación")
  @GetMapping
  public ResponseEntity<List<SolicitudMedicoDTO>> pendientes() {
    return ResponseEntity.ok(adminService.solicitudesPendientes());
  }

  @Operation(summary = "Aprueba un perfil profesional y habilita su acceso")
  @PostMapping("/{doctorId}/approve")
  public ResponseEntity<SolicitudMedicoDTO> aprobar(@PathVariable Long doctorId) {
    return ResponseEntity.ok(adminService.aprobar(doctorId));
  }

  @Operation(summary = "Rechaza una solicitud e indica el motivo")
  @PostMapping("/{doctorId}/reject")
  public ResponseEntity<SolicitudMedicoDTO> rechazar(
      @PathVariable Long doctorId,
      @Valid @RequestBody RechazoSolicitudDTO request) {
    return ResponseEntity.ok(adminService.rechazar(doctorId, request.getMotivo()));
  }
}
