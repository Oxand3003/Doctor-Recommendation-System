package utp.edu.pe.recomendaciones.Controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import utp.edu.pe.recomendaciones.service.CuentaService;

/**
 * "Mi cuenta" (/account) muestra solo los datos personales; las citas pendientes y el historial clínico del
 * paciente tienen su propia página (/mis-citas). El buscador de médicos está en el inicio.
 */
@Controller
public class AccountController {

    private final CuentaService cuentas;

    public AccountController(CuentaService cuentas) {
        this.cuentas = cuentas;
    }

    @GetMapping("/account")
    public String account(Authentication auth, Model model,
                          @RequestParam(name = "modo", required = false) String modo) {
        // Los médicos entran directo a su perfil privado (/medico/perfil).
        if (tieneRol(auth, "ROLE_MEDICO")) return "redirect:/medico/perfil";
        // Enlaces antiguos al buscador que estaba en esta página (/account?modo=ia): ahora vive en el inicio.
        if (modo != null) return "redirect:/?modo=" + ("manual".equals(modo) ? "manual" : "ia") + "#patient-search";
        model.addAttribute("cuenta", cuentas.cuenta(auth.getName()));
        return "account/account";
    }

    @GetMapping("/mis-citas")
    public String citas(Authentication auth, Model model) {
        if (tieneRol(auth, "ROLE_MEDICO")) return "redirect:/medico/agenda";
        if (!tieneRol(auth, "ROLE_PACIENTE")) return "redirect:/account";
        model.addAttribute("cuenta", cuentas.cuenta(auth.getName()));
        return "account/citas";
    }

    private static boolean tieneRol(Authentication auth, String rol) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(rol::equals);
    }
}
