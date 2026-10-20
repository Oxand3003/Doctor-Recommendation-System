package utp.edu.pe.recomendaciones.Controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.service.CredencialesMedicoService;

/**
 * Cambio de contraseña y de correo desde el perfil privado del médico. Requiere sesión iniciada
 * (anyRequest en SecurityConfig, con CSRF); el servicio exige además rol MEDICO y cuenta ACTIVA, y el usuario se
 * toma SIEMPRE de la sesión: estos endpoints no reciben ningún identificador de cuenta.
 *
 * Tras un cambio correcto se cierra la sesión: con el correo nuevo, la sesión actual quedaría apuntando a un
 * correo que ya no existe; con la contraseña nueva, es lo más seguro. El médico vuelve a entrar con los datos nuevos.
 */
@Controller
@RequestMapping("/medico/credenciales")
public class MedicoCredencialesController {

    private static final String PERFIL = "redirect:/medico/perfil";

    private final CredencialesMedicoService credenciales;

    public MedicoCredencialesController(CredencialesMedicoService credenciales) {
        this.credenciales = credenciales;
    }

    // ---------------- Contraseña ----------------
    @PostMapping("/password/codigo")
    public String pedirCodigoPassword(Authentication auth, RedirectAttributes ra) {
        try {
            credenciales.solicitarCodigoPassword(auth.getName());
            ra.addFlashAttribute("ok", "Te enviamos un código de seguridad a tu correo registrado.");
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        ra.addFlashAttribute("reabrir", "password");
        return PERFIL;
    }

    @PostMapping("/password")
    public String cambiarPassword(@RequestParam(defaultValue = "") String codigo,
                                  @RequestParam(defaultValue = "") String nuevaPassword,
                                  @RequestParam(defaultValue = "") String confirmarPassword,
                                  Authentication auth, HttpServletRequest request, HttpServletResponse response,
                                  RedirectAttributes ra) {
        try {
            credenciales.cambiarPassword(auth.getName(), codigo, nuevaPassword, confirmarPassword);
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("reabrir", "password");
            return PERFIL;
        }
        cerrarSesion(request, response, auth);
        return "redirect:/login?cambio=password";
    }

    // ---------------- Correo ----------------
    @PostMapping("/email/codigo")
    public String pedirCodigoEmail(@RequestParam(defaultValue = "") String nuevoEmail,
                                   @RequestParam(defaultValue = "") String confirmarEmail,
                                   Authentication auth, RedirectAttributes ra) {
        try {
            credenciales.solicitarCodigoEmail(auth.getName(), nuevoEmail, confirmarEmail);
            ra.addFlashAttribute("ok", "Te enviamos un código de seguridad a tu correo registrado actual.");
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        ra.addFlashAttribute("reabrir", "email");
        return PERFIL;
    }

    @PostMapping("/email")
    public String cambiarEmail(@RequestParam(defaultValue = "") String codigo,
                               Authentication auth, HttpServletRequest request, HttpServletResponse response,
                               RedirectAttributes ra) {
        try {
            credenciales.cambiarEmail(auth.getName(), codigo);
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("reabrir", "email");
            return PERFIL;
        } catch (DataIntegrityViolationException e) {
            // Otra cuenta registró ese correo justo ahora: la restricción UNIQUE lo impidió y no se guardó nada.
            ra.addFlashAttribute("error", "Ese correo ya está registrado por otra cuenta. Elige otro y pide un código nuevo.");
            ra.addFlashAttribute("reabrir", "email");
            return PERFIL;
        }
        cerrarSesion(request, response, auth);
        return "redirect:/login?cambio=email";
    }

    private void cerrarSesion(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        new SecurityContextLogoutHandler().logout(request, response, auth);
    }
}
