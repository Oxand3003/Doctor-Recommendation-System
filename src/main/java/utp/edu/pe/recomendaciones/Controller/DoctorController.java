package utp.edu.pe.recomendaciones.Controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import utp.edu.pe.recomendaciones.dto.SlotView;
import utp.edu.pe.recomendaciones.model.enums.Modalidad;
import utp.edu.pe.recomendaciones.service.CitaPacienteService;
import utp.edu.pe.recomendaciones.service.DoctorProfileService;

@Controller
@RequestMapping("/doctor")   // /doctor/** PUBLICO en SecurityConfig
public class DoctorController {

    private final DoctorProfileService service;
    private final CitaPacienteService citas;

    public DoctorController(DoctorProfileService service, CitaPacienteService citas) {
        this.service = service;
        this.citas = citas;
    }

    /** Enlaces de ejemplo del home (/doctor/profile): llevan al primer perfil. */
    @GetMapping("/profile")
    public String perfilEjemplo() {
        return "redirect:/doctor/1";
    }

    /** Página: /doctor/1 */
    @GetMapping("/{id}")
    public String perfil(@PathVariable Long id, Authentication auth, Model model) {
        model.addAttribute("doc", service.getProfile(id));
        // Quién mira el perfil (paciente / anónimo / otro rol) y sus datos, para la ventana de reserva.
        model.addAttribute("reserva", citas.perfilReserva(auth == null ? null : auth.getName()));
        return "doctor/profile";
    }

    /** JSON: /doctor/1/slots?date=2026-03-16&modalidad=PRESENCIAL */
    @GetMapping("/{id}/slots")
    @ResponseBody
    public List<SlotView> slots(@PathVariable Long id,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                @RequestParam(defaultValue = "PRESENCIAL") Modalidad modalidad) {
        return service.getSlots(id, date, modalidad);
    }
}
