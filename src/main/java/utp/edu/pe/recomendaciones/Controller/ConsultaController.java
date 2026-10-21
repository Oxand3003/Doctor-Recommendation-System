package utp.edu.pe.recomendaciones.Controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import utp.edu.pe.recomendaciones.dto.ConsultaForm;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.service.ConsultaService;

/** Recibe el formulario de contacto del inicio y lo guarda en la tabla consultas. */
@Controller
public class ConsultaController {

    private final ConsultaService consultas;

    public ConsultaController(ConsultaService consultas) {
        this.consultas = consultas;
    }

    @PostMapping("/consultas")
    public String enviar(@RequestParam Map<String, String> p, Authentication auth, RedirectAttributes ra) {
        // Campo trampa para bots: un humano no lo ve ni lo llena. Se responde "ok" sin guardar nada.
        if (p.get("sitioWeb") != null && !p.get("sitioWeb").isBlank()) {
            ra.addFlashAttribute("consultaOk", "¡Gracias! Recibimos tu consulta.");
            return "redirect:/#contacto";
        }
        try {
            String emailSesion = (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) ? auth.getName() : null;
            consultas.registrar(new ConsultaForm(
                    p.get("nombres"), p.get("correo"), p.get("telefono"),
                    p.get("motivo"), p.get("mensaje")), emailSesion);
            ra.addFlashAttribute("consultaOk",
                    "¡Gracias! Recibimos tu consulta y te responderemos al correo o teléfono que indicaste.");
        } catch (NegocioException e) {
            Map<String, String> valores = new HashMap<>();
            for (String k : new String[]{"nombres", "correo", "telefono", "motivo", "mensaje"}) {
                valores.put(k, p.getOrDefault(k, ""));
            }
            ra.addFlashAttribute("consultaError", e.getMessage());
            ra.addFlashAttribute("consultaForm", valores);
        }
        return "redirect:/#contacto";
    }
}
