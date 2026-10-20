package utp.edu.pe.recomendaciones.Controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import utp.edu.pe.recomendaciones.dto.ReservaCreada;
import utp.edu.pe.recomendaciones.exception.ConflictoReservaException;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.service.CitaPacienteService;
import utp.edu.pe.recomendaciones.service.NotificacionCitaService;

/**
 * POST /appointments: reserva una cita para el paciente de la sesión (solo ROLE_PACIENTE, ver SecurityConfig;
 * CSRF activo). El paciente nunca viaja en la petición: sale de la sesión. Responde siempre JSON:
 *  201 {ok:true, cita:{...}}  ·  400 datos inválidos  ·  409 turno ya tomado  ·  401 sin sesión.
 */
@RestController
@RequestMapping("/appointments")
public class CitaPacienteController {

    private final CitaPacienteService citas;
    private final NotificacionCitaService notificaciones;

    public CitaPacienteController(CitaPacienteService citas, NotificacionCitaService notificaciones) {
        this.citas = citas;
        this.notificaciones = notificaciones;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> reservar(
            Authentication auth,
            @RequestParam(required = false) String medicoId,
            @RequestParam(required = false) String fecha,
            @RequestParam(required = false) String hora,
            @RequestParam(required = false) String modalidad,
            @RequestParam(required = false) String motivo) {
        if (auth == null) {
            return error(HttpStatus.UNAUTHORIZED, "Inicia sesión como paciente para reservar una cita.");
        }
        try {
            ReservaCreada r = citas.reservar(auth.getName(), medicoId, fecha, hora, modalidad, motivo);
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("ok", true);
            cuerpo.put("cita", r);
            // La reserva ya está guardada; el correo de confirmación no puede deshacerla si falla.
            String nombre = citas.perfilReserva(auth.getName()).nombre();
            cuerpo.put("correoEnviado", notificaciones.reservaCreada(auth.getName(), nombre, r));
            return ResponseEntity.status(HttpStatus.CREATED).body(cuerpo);
        } catch (ConflictoReservaException e) {
            return error(HttpStatus.CONFLICT, e.getMessage());
        } catch (NegocioException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException e) {
            // Última barrera: el UNIQUE (médico, inicio) de la tabla impidió una cita duplicada.
            return error(HttpStatus.CONFLICT,
                    "Ese horario acaba de ser reservado por otro paciente. Elige otro turno.");
        }
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus estado, String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", false);
        cuerpo.put("message", mensaje);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
