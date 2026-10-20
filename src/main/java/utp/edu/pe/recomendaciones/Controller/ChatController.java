package utp.edu.pe.recomendaciones.Controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;

import utp.edu.pe.recomendaciones.service.CuentaService;

/**
 * Pantalla de mensajería paciente-médico (SOLO VISTA).
 *
 * Una única ruta sirve a los dos perfiles: la plantilla se adapta según el rol. La ruta cae bajo
 * anyRequest().authenticated() de SecurityConfig, así que exige sesión; el administrador vuelve a /account.
 * La vista usa el servicio REST/STOMP del frontend; el backend persiste en MensajeriaService.
 */
@Controller
public class ChatController {

    private final CuentaService cuentas;

    public ChatController(CuentaService cuentas) {
        this.cuentas = cuentas;
    }

    @GetMapping("/chat")
    public String chat(Authentication auth, Model model, CsrfToken csrfToken, HttpServletRequest request) {
        boolean medico = tieneRol(auth, "ROLE_MEDICO");
        boolean paciente = tieneRol(auth, "ROLE_PACIENTE");
        if (!medico && !paciente) {
            return "redirect:/account";
        }
        model.addAttribute("chatRol", medico ? "MEDICO" : "PACIENTE");
        model.addAttribute("chatNombre", cuentas.cuenta(auth.getName()).nombre());
        // Guarda exactamente la variante enmascarada que Thymeleaf expone al cliente para validar STOMP CONNECT.
        request.getSession().setAttribute(utp.edu.pe.recomendaciones.config.ChatCsrfHandshakeInterceptor.SESSION_TOKEN_ATTRIBUTE,
                csrfToken.getToken());
        return "chat/chat";
    }

    private static boolean tieneRol(Authentication auth, String rol) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(rol::equals);
    }
}
