package utp.edu.pe.recomendaciones.Controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.service.ValoracionPacienteService;

@Controller
@RequestMapping("/valoraciones")
public class ValoracionPacienteController {

    private final ValoracionPacienteService valoraciones;

    public ValoracionPacienteController(ValoracionPacienteService valoraciones) {
        this.valoraciones = valoraciones;
    }

    @PostMapping
    public String guardar(@RequestParam Long citaId, @RequestParam int puntaje,
                          @RequestParam String comentario, Authentication auth,
                          RedirectAttributes ra) {
        try {
            valoraciones.guardar(auth.getName(), citaId, puntaje, comentario);
            ra.addFlashAttribute("ok", "Tu comentario fue publicado correctamente.");
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/account#clinical-history";
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            valoraciones.eliminar(auth.getName(), id);
            ra.addFlashAttribute("ok", "Tu comentario fue eliminado.");
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/account#clinical-history";
    }
}
