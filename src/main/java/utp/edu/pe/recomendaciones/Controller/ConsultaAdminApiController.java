package utp.edu.pe.recomendaciones.Controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import utp.edu.pe.recomendaciones.dto.ConsultaView;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.service.ConsultaService;

/** JSON de la sección «Consultas» del panel admin. Protegido por /admin/** -> ROLE_ADMIN en SecurityConfig. */
@RestController
@RequestMapping("/admin/api/consultas")
public class ConsultaAdminApiController {

    private final ConsultaService consultas;

    public ConsultaAdminApiController(ConsultaService consultas) {
        this.consultas = consultas;
    }

    @GetMapping
    public List<ConsultaView> listar() {
        return consultas.listar();
    }

    @PostMapping("/{id}/reply")
    public ConsultaView responder(@PathVariable Long id,
                                  @RequestParam(defaultValue = "") String subject,
                                  @RequestParam(defaultValue = "") String message,
                                  @RequestParam(defaultValue = "true") boolean archive,
                                  Authentication auth) {
        return consultas.responderPorCorreo(id, subject, message, archive, auth.getName());
    }

    @PostMapping("/{id}/attend")
    public ConsultaView atender(@PathVariable Long id,
                                @RequestParam(defaultValue = "OTRO") String channel,
                                Authentication auth) {
        return consultas.marcarAtendida(id, channel, auth.getName());
    }

    @PostMapping("/{id}/reopen")
    public ConsultaView reabrir(@PathVariable Long id) {
        return consultas.reabrir(id);
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<Map<String, String>> negocio(NegocioException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }
}
