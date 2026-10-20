package utp.edu.pe.recomendaciones.config;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Le dice al navbar (fragments/navbar.html) si quien navega es un médico (o administrador) con sesión iniciada, para mostrar
 * "Mi perfil" en todo el sitio. Solo lee el rol (ROLE_MEDICO) del SecurityContext: no autentica ni autoriza
 * nada, y las rutas siguen protegidas por SecurityConfig y por los servicios.
 * Se expone un record (no un boolean) para que Spring nunca lo agregue como parámetro en una redirección.
 */
@ControllerAdvice
public class NavbarAdvice {

    /** Datos mínimos del visitante para dibujar el navbar. */
    public record NavInfo(boolean medico, boolean paciente, boolean admin) {
    }

    @ModelAttribute("nav")
    public NavInfo nav() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return new NavInfo(esRol(auth, "ROLE_MEDICO"), esRol(auth, "ROLE_PACIENTE"), esRol(auth, "ROLE_ADMIN"));
    }

    private static boolean esRol(Authentication auth, String rol) {
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(rol::equals);
    }
}
