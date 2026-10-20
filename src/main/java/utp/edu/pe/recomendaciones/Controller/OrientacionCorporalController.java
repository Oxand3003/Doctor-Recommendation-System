package utp.edu.pe.recomendaciones.Controller;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import utp.edu.pe.recomendaciones.dto.OrientacionCorporalRequest;
import utp.edu.pe.recomendaciones.dto.OrientacionCorporalResponse;
import utp.edu.pe.recomendaciones.service.OrientacionCorporalService;

/**
 * Orientación por zona corporal (maniquí del asistente de IA). Queda bajo /api/chat/**, por lo que ya está
 * protegida: solo PACIENTE y sin CSRF, igual que el resto del asistente (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/chat")
public class OrientacionCorporalController {
    private final OrientacionCorporalService orientacion;

    public OrientacionCorporalController(OrientacionCorporalService orientacion) {
        this.orientacion = orientacion;
    }

    @PostMapping("/orientacion")
    public OrientacionCorporalResponse orientar(@Valid @RequestBody OrientacionCorporalRequest request) {
        return orientacion.orientar(request);
    }
}