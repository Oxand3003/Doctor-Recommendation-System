package utp.edu.pe.recomendacion_citas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController 
@RequestMapping("/api/v1/doctors")
@Tag(name = "Doctores", description = "Consulta y recomendación de médicos")
public class DoctorController {

  private final DoctorService doctorService;
  private final RecomendacionService recomendacionService;

  public DoctorController(DoctorService doctorService, RecomendacionService recomendacionService) {
    this.doctorService = doctorService;
    this.recomendacionService = recomendacionService;
  }

  @Operation (summary = "Lista todos los medicos resgistrados")
  @GetMapping
  public ResponseEntity<List<Doctor>> listar() {
    return ResponseEntity.ok(doctorService.listarTodos());
  }
}
