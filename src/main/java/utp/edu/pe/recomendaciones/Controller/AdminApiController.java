package utp.edu.pe.recomendaciones.Controller;

import java.util.HashMap;
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

import utp.edu.pe.recomendaciones.dto.AdminDataView;
import utp.edu.pe.recomendaciones.dto.DecisionMedico;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.service.AdminService;
import utp.edu.pe.recomendaciones.service.NotificacionMedicoService;

/** JSON del panel admin. Protegido por /admin/** -> ROLE_ADMIN en SecurityConfig. */
@RestController
@RequestMapping("/admin/api")
public class AdminApiController {

    private static final Map<String, Object> OK = Map.of("ok", true);

    private final AdminService admin;
    private final NotificacionMedicoService notificaciones;

    public AdminApiController(AdminService admin, NotificacionMedicoService notificaciones) {
        this.admin = admin;
        this.notificaciones = notificaciones;
    }

    @GetMapping("/data")
    public AdminDataView data() {
        return admin.datos();
    }

    @PostMapping("/applications/{id}/approve")
    public Map<String, Object> aprobar(@PathVariable Long id, Authentication auth) {
        // La decisión ya está confirmada en la base de datos cuando admin.aprobar() devuelve; recién ahí se avisa.
        // Una solicitud ya revisada lanza NegocioException antes de llegar aquí, así que nunca hay dos correos.
        return conAviso(notificaciones, admin.aprobar(id, auth.getName()));
    }

    @PostMapping("/applications/{id}/reject")
    public Map<String, Object> rechazar(@PathVariable Long id,
                                        @RequestParam(defaultValue = "") String reason,
                                        Authentication auth) {
        return conAviso(notificaciones, admin.rechazar(id, reason, auth.getName()));
    }

    @PostMapping("/doctors/{id}/suspend")
    public Map<String, Object> suspender(@PathVariable Long id, Authentication auth) {
        admin.suspender(id, auth.getName());
        return OK;
    }

    @PostMapping("/doctors/{id}/reactivate")
    public Map<String, Object> reactivar(@PathVariable Long id, Authentication auth) {
        admin.reactivar(id, auth.getName());
        return OK;
    }

    @PostMapping("/doctors/{id}/delete")
    public Map<String, Object> eliminar(@PathVariable Long id, Authentication auth) {
        admin.eliminar(id, auth.getName());
        return OK;
    }

    @PostMapping("/patients/{id}/suspend")
    public Map<String, Object> suspenderPaciente(@PathVariable Long id, Authentication auth) {
        admin.suspenderPaciente(id, auth.getName());
        return OK;
    }

    @PostMapping("/patients/{id}/reactivate")
    public Map<String, Object> reactivarPaciente(@PathVariable Long id, Authentication auth) {
        admin.reactivarPaciente(id, auth.getName());
        return OK;
    }

    /** {"ok":true} y, si el correo no pudo enviarse, {"warning":"..."} para que el panel lo muestre. */
    private static Map<String, Object> conAviso(NotificacionMedicoService notificaciones, DecisionMedico decision) {
        String aviso = notificaciones.notificar(decision);
        if (aviso == null) return OK;
        Map<String, Object> respuesta = new HashMap<>(OK);
        respuesta.put("warning", aviso);
        return respuesta;
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<Map<String, String>> negocio(NegocioException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
    }
}
