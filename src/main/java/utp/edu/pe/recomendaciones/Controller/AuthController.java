package utp.edu.pe.recomendaciones.Controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import utp.edu.pe.recomendaciones.dto.RegistroMedicoForm;
import utp.edu.pe.recomendaciones.dto.RegistroPacienteForm;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.service.RegistroService;

@Controller
public class AuthController {

    private final RegistroService registro;
    private final int loginBloqueoMinutos;

    public AuthController(RegistroService registro,
                          @Value("${medicerca.security.login-bloqueo-minutos:15}") int loginBloqueoMinutos) {
        this.registro = registro;
        this.loginBloqueoMinutos = loginBloqueoMinutos;
    }

    @GetMapping("/login")
    public String login(Authentication auth, Model model) {
        // Un administrador con sesión vigente no necesita el formulario: va al panel.
        // Si aún no completó el OTP, AdminTwoFactorFilter lo lleva a /admin/verify (nunca se salta la verificación).
        if (auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return "redirect:/admin/doctor-applications";
        }
        model.addAttribute("specialties", registro.especialidades());
        model.addAttribute("establishments", registro.establecimientos());
        model.addAttribute("loginBloqueoMinutos", loginBloqueoMinutos);
        return "auth/auth";
    }

    @PostMapping("/register/patient")
    public String registrarPaciente(@RequestParam Map<String, String> p, RedirectAttributes ra) {
        try {
            registro.registrarPaciente(new RegistroPacienteForm(
                    p.get("nombres"), p.get("apellidos"), p.get("dni"),
                    p.get("telefono"), p.get("email"), p.get("password")));
            ra.addFlashAttribute("success", "¡Cuenta creada! Ya puedes iniciar sesión con tu correo y contraseña.");
            return "redirect:/login";
        } catch (NegocioException e) {
            return volver(ra, p, e.getMessage(), "register-patient");
        } catch (DataIntegrityViolationException e) {
            return volver(ra, p, "Ya existe una cuenta con ese correo o DNI.", "register-patient");
        }
    }

    @PostMapping("/register/doctor")
    public String registrarMedico(@RequestParam Map<String, String> p, RedirectAttributes ra) {
        try {
            registro.registrarMedico(new RegistroMedicoForm(
                    p.get("nombres"), p.get("apellidos"), p.get("dni"), p.get("telefono"),
                    p.get("email"), p.get("password"),
                    p.get("cmp"), p.get("rne"), p.get("tituloProfesional"), p.get("universidad"),
                    entero(p.get("anioEgreso")), entero(p.get("aniosExperiencia")),
                    largo(p.get("especialidadId")), largo(p.get("establecimientoId")),
                    p.get("sustentoUrl")));
            ra.addFlashAttribute("success",
                    "Solicitud enviada. Un administrador revisará tus credenciales; "
                            + "podrás iniciar sesión cuando sea aprobada.");
            return "redirect:/login";
        } catch (NegocioException e) {
            return volver(ra, p, e.getMessage(), "register-doctor");
        } catch (DataIntegrityViolationException e) {
            return volver(ra, p, "Ya existe una cuenta con ese correo, DNI o CMP.", "register-doctor");
        }
    }

    /** Vuelve al formulario con el error y los datos escritos (sin la contraseña). */
    private String volver(RedirectAttributes ra, Map<String, String> p, String error, String tab) {
        Map<String, String> valores = new HashMap<>(p);
        valores.remove("password");
        valores.remove("_csrf");
        ra.addFlashAttribute("error", error);
        ra.addFlashAttribute("f", valores);
        return "redirect:/login#" + tab;
    }

    private Integer entero(String s) {
        try {
            return s == null || s.isBlank() ? null : Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long largo(String s) {
        try {
            return s == null || s.isBlank() ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
