package utp.edu.pe.recomendaciones.config;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

/**
 * Bloquea una cuenta temporalmente tras varios intentos de inicio de sesión fallidos seguidos.
 * Una cuenta ya bloqueada o inactiva falla en UsuarioDetailsService (Locked/DisabledException) antes
 * de comparar la contraseña, así que este listener solo ve contraseñas incorrectas de cuentas activas.
 */
@Component
public class LoginIntentosListener {

    private static final Logger log = LoggerFactory.getLogger(LoginIntentosListener.class);

    private final UsuarioRepository usuarios;
    private final int maxIntentos;
    private final int bloqueoMinutos;

    public LoginIntentosListener(UsuarioRepository usuarios,
                                  @Value("${medicerca.security.login-max-intentos:3}") int maxIntentos,
                                  @Value("${medicerca.security.login-bloqueo-minutos:15}") int bloqueoMinutos) {
        this.usuarios = usuarios;
        this.maxIntentos = Math.max(1, maxIntentos);
        this.bloqueoMinutos = Math.max(1, bloqueoMinutos);
    }

    @EventListener
    @Transactional
    public void alFallar(AuthenticationFailureBadCredentialsEvent evento) {
        String email = String.valueOf(evento.getAuthentication().getPrincipal()).trim().toLowerCase();
        usuarios.findByEmail(email).ifPresent(u -> {
            u.setIntentosFallidos(u.getIntentosFallidos() + 1);
            if (u.getIntentosFallidos() >= maxIntentos) {
                u.setBloqueadoHasta(LocalDateTime.now().plusMinutes(bloqueoMinutos));
                u.setIntentosFallidos(0);
                log.info("Cuenta bloqueada {} minutos por intentos fallidos: {}", bloqueoMinutos, enmascarar(email));
            }
            usuarios.save(u);
        });
    }

    @EventListener
    @Transactional
    public void alEntrar(AuthenticationSuccessEvent evento) {
        String email = evento.getAuthentication().getName().trim().toLowerCase();
        usuarios.findByEmail(email).ifPresent(u -> {
            if (u.getIntentosFallidos() != 0 || u.getBloqueadoHasta() != null) {
                u.setIntentosFallidos(0);
                u.setBloqueadoHasta(null);
                usuarios.save(u);
            }
        });
    }

    private static String enmascarar(String email) {
        int arroba = email.indexOf('@');
        return arroba <= 1 ? "***" : email.charAt(0) + "***" + email.substring(arroba);
    }
}
