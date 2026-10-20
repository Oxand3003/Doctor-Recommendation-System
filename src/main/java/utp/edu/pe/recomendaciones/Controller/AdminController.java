package utp.edu.pe.recomendaciones.Controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.service.AdminAccessService;
import utp.edu.pe.recomendaciones.service.AdminGestionService;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class AdminController {
    private final AdminAccessService access;
    private final AdminGestionService gestion;

    public AdminController(AdminAccessService access, AdminGestionService gestion) {
        this.access = access;
        this.gestion = gestion;
    }

    @GetMapping("/admin/doctor-applications")
    public String doctorApplications(Authentication auth, Model model) {
        model.addAttribute("adminEmail", auth.getName());
        return "admin/doctor-applications";
    }

    @GetMapping("/admin/verify")
    public String verifyPage(Authentication auth, Model model, jakarta.servlet.http.HttpSession session) {
        // Sesión ya verificada: no se vuelve a pedir el código.
        if (Boolean.TRUE.equals(session.getAttribute(AdminAccessService.SESSION_VERIFIED))) {
            return "redirect:/admin/doctor-applications";
        }
        model.addAttribute("adminEmail", auth.getName());
        return "admin/verify";
    }

    @PostMapping("/admin/verify")
    public String verify(@RequestParam String code, Authentication auth, jakarta.servlet.http.HttpSession session,
                         RedirectAttributes ra) {
        try {
            access.verificarLogin(auth.getName(), code, session);
            // Flash: solo vive en la siguiente petición, así la bienvenida aparece una vez por inicio de sesión.
            ra.addFlashAttribute("welcome", Boolean.TRUE);
            return "redirect:/admin/doctor-applications";
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/verify";
        }
    }

    @PostMapping("/admin/verify/resend")
    public String resend(Authentication auth, RedirectAttributes ra) {
        try { access.emitirCodigoLogin(auth.getName()); ra.addFlashAttribute("ok", "Código reenviado."); }
        catch (NegocioException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/verify";
    }

    @GetMapping("/admin/administrators")
    public String administrators(Authentication auth, Model model) {
        if (!access.puedeGestionar(auth.getName())) return "error/403";
        model.addAttribute("adminEmail", auth.getName());
        model.addAttribute("gestion", gestion.ver(auth.getName()));
        return "admin/administrators";
    }

    @PostMapping("/admin/administrators/request")
    public String requestAdmin(@RequestParam String email, @RequestParam String password,
                               Authentication auth, RedirectAttributes ra) {
        try { access.solicitarAlta(auth.getName(), email, password); ra.addFlashAttribute("ok", "Código enviado al correo indicado."); }
        catch (NegocioException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/administrators";
    }

    @PostMapping("/admin/administrators/confirm")
    public String confirmAdmin(@RequestParam String code, Authentication auth, RedirectAttributes ra) {
        try { access.confirmarAlta(auth.getName(), code); ra.addFlashAttribute("ok", "Administrador creado correctamente."); }
        catch (NegocioException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/administrators";
    }

    /** Inicia la solicitud de desactivación de otra cuenta de administrador (cuenta como el primer voto). */
    @PostMapping("/admin/administrators/deactivation/request")
    public String requestDeactivation(@RequestParam Long targetId, @RequestParam String reason,
                                      Authentication auth, RedirectAttributes ra) {
        try {
            gestion.solicitarDesactivacion(auth.getName(), targetId, reason);
            ra.addFlashAttribute("ok", "Solicitud creada con tu voto. Falta que otro administrador la apruebe.");
        } catch (NegocioException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/administrators#solicitudes";
    }

    @PostMapping("/admin/administrators/deactivation/{id}/approve")
    public String approveDeactivation(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try { ra.addFlashAttribute("ok", gestion.aprobar(auth.getName(), id)); }
        catch (NegocioException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/administrators#solicitudes";
    }

    @PostMapping("/admin/administrators/deactivation/{id}/reject")
    public String rejectDeactivation(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try { gestion.rechazar(auth.getName(), id); ra.addFlashAttribute("ok", "Solicitud rechazada."); }
        catch (NegocioException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/administrators#solicitudes";
    }

    @PostMapping("/admin/administrators/deactivation/{id}/cancel")
    public String cancelDeactivation(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try { gestion.cancelar(auth.getName(), id); ra.addFlashAttribute("ok", "Solicitud cancelada."); }
        catch (NegocioException e) { ra.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/admin/administrators#solicitudes";
    }
}
