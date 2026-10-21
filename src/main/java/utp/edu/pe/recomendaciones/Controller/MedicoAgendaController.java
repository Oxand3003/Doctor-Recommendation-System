package utp.edu.pe.recomendaciones.Controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.dto.CitaCancelada;
import utp.edu.pe.recomendaciones.service.AgendaMedicoService;
import utp.edu.pe.recomendaciones.service.NotificacionCitaService;

/**
 * Dashboard, agenda y detalle de citas del médico (solo lectura). Misma protección que el resto de
 * /medico/**: sesión obligatoria, rol MEDICO y cuenta ACTIVA comprobados en el servicio, y el médico sale
 * siempre de la sesión, nunca de la URL.
 */
@Controller
@RequestMapping("/medico")
public class MedicoAgendaController {

    private final AgendaMedicoService agenda;
    private final NotificacionCitaService notificaciones;

    public MedicoAgendaController(AgendaMedicoService agenda, NotificacionCitaService notificaciones) {
        this.agenda = agenda;
        this.notificaciones = notificaciones;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model) {
        if (!esMedico(auth)) return "redirect:/account";
        model.addAttribute("d", agenda.dashboard(auth.getName()));
        return "medico/dashboard";
    }

    @GetMapping("/agenda")
    public String agenda(@RequestParam(defaultValue = "semana") String vista,
                         @RequestParam(required = false) String fecha,
                         @RequestParam(defaultValue = "pendientes") String estado,
                         Authentication auth, Model model) {
        if (!esMedico(auth)) return "redirect:/account";
        model.addAttribute("a", agenda.agenda(auth.getName(), vista, fecha(fecha), "atendidas".equals(estado)));
        return "medico/agenda";
    }

    @GetMapping("/citas/{id}")
    public String detalle(@PathVariable Long id, Authentication auth, Model model, RedirectAttributes ra) {
        if (!esMedico(auth)) return "redirect:/account";
        try {
            model.addAttribute("c", agenda.detalle(auth.getName(), id));
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/medico/agenda";
        }
        return "medico/cita";
    }

    /** Elimina (cancela) una reserva del médico y avisa al paciente por correo. */
    @PostMapping("/citas/{id}/eliminar")
    public String eliminar(@PathVariable Long id, @RequestParam(required = false) String motivo,
                           Authentication auth, RedirectAttributes ra) {
        if (!esMedico(auth)) return "redirect:/account";
        try {
            CitaCancelada c = agenda.eliminar(auth.getName(), id, motivo);
            // La eliminación ya quedó guardada; si el correo falla, el médico lo ve y puede avisar por WhatsApp.
            if (notificaciones.citaEliminada(c)) {
                ra.addFlashAttribute("ok", "Reserva eliminada. Se notificó al paciente por correo.");
            } else {
                ra.addFlashAttribute("error", "La reserva se eliminó, pero no se pudo enviar el correo al paciente. "
                        + "Avísale por WhatsApp.");
            }
            return "redirect:/medico/agenda?vista=dia&fecha=" + c.fechaIso();
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/medico/citas/" + id;
        }
    }

    private LocalDate fecha(String s) {
        try {
            return s == null || s.isBlank() ? null : LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean esMedico(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_MEDICO"::equals);
    }
}
