package utp.edu.pe.recomendaciones.config;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

@Component
public class AdminTwoFactorFilter extends OncePerRequestFilter {
    private final UsuarioRepository usuarios;

    public AdminTwoFactorFilter(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        // Un administrador suspendido pierde la sesión que tuviera abierta (no solo el inicio de sesión futuro).
        boolean estatico = path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/");
        if (admin && !estatico && !usuarios.findByEmail(authentication.getName())
                .map(u -> u.getEstado() == EstadoUsuario.ACTIVO).orElse(false)) {
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        boolean verificationRoute = "/admin/verify".equals(path) || "/admin/verify/resend".equals(path);
        if (admin && path.startsWith("/admin") && !verificationRoute
                && !Boolean.TRUE.equals(request.getSession(false) == null ? null
                        : request.getSession(false).getAttribute("MEDICERCA_ADMIN_2FA_VERIFIED"))) {
            response.sendRedirect(request.getContextPath() + "/admin/verify");
            return;
        }
        chain.doFilter(request, response);
    }
}
