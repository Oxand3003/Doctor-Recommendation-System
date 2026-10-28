package utp.edu.pe.recomendaciones.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.service.RecuperacionPasswordService;

/**
 * "Olvidé mi contraseña" desde /login, sin sesión iniciada. Público (ver SecurityConfig:
 * /recuperar-password/**), protegido solo por los límites del propio código de seguridad
 * (espera entre envíos, tope por hora, máximo de intentos) que ya aplica RecuperacionPasswordService.
 */
@Controller
@RequestMapping("/recuperar-password")
public class RecuperacionPasswordController {

    private final RecuperacionPasswordService recuperacion;

    public RecuperacionPasswordController(RecuperacionPasswordService recuperacion) {
        this.recuperacion = recuperacion;
    }

    @PostMapping("/codigo")
    public String pedirCodigo(@RequestParam(defaultValue = "") String email, RedirectAttributes ra) {
        try {
            recuperacion.solicitarCodigo(email);
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("reabrir", "recuperar");
            ra.addFlashAttribute("recuperarEmail", email);
            return "redirect:/login";
        }
        // Mismo mensaje exista o no la cuenta: no se revela si el correo está registrado.
        ra.addFlashAttribute("success", "Si el correo está registrado, te enviamos un código de 6 dígitos para restablecer tu contraseña.");
        ra.addFlashAttribute("reabrir", "recuperar-codigo");
        ra.addFlashAttribute("recuperarEmail", email);
        return "redirect:/login";
    }

    @PostMapping
    public String cambiarPassword(@RequestParam(defaultValue = "") String email,
                                  @RequestParam(defaultValue = "") String codigo,
                                  @RequestParam(defaultValue = "") String nuevaPassword,
                                  @RequestParam(defaultValue = "") String confirmarPassword,
                                  RedirectAttributes ra) {
        try {
            recuperacion.cambiarPassword(email, codigo, nuevaPassword, confirmarPassword);
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("reabrir", "recuperar-codigo");
            ra.addFlashAttribute("recuperarEmail", email);
            return "redirect:/login";
        }
        return "redirect:/login?cambio=password";
    }
}
