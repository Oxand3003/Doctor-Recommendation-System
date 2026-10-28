package utp.edu.pe.recomendaciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Admin -> panel de solicitudes; cualquier otro -> /account. */
    @Bean
    AuthenticationSuccessHandler loginSuccessHandler(utp.edu.pe.recomendaciones.service.AdminAccessService adminAccess) {
        return (request, response, authentication) -> {
            boolean admin = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch("ROLE_ADMIN"::equals);
            if (admin) {
                request.getSession(true).removeAttribute(utp.edu.pe.recomendaciones.service.AdminAccessService.SESSION_VERIFIED);
                try {
                    adminAccess.emitirCodigoLogin(authentication.getName());
                } catch (RuntimeException ignored) {
                    // El usuario queda detenido por el filtro hasta que pueda solicitar un código válido.
                }
                response.sendRedirect(request.getContextPath() + "/admin/verify");
            } else {
                response.sendRedirect(request.getContextPath() + "/account");
            }
        };
    }

    /** Distingue "cuenta no activa", "cuenta bloqueada por intentos fallidos" y "credenciales incorrectas". */
    @Bean
    AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            String param = exception instanceof LockedException ? "locked"
                    : exception instanceof DisabledException ? "disabled"
                    : "error";
            response.sendRedirect(request.getContextPath() + "/login?" + param);
        };
    }

    /**
     * Las llamadas de API no deben redirigirse al formulario de login: conservan el
     * contrato HTTP 401 y dejan que Spring Boot renderice error/401.html.
     */
    @Bean
    AuthenticationEntryPoint apiAuthenticationEntryPoint() {
        return (request, response, authenticationException) -> response.sendError(401);
    }

    /** Conserva el 403 y delega la representación HTML en el manejador de errores de Spring Boot. */
    @Bean
    AccessDeniedHandler htmlAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> response.sendError(403);
    }

    /** Matcher compatible con Spring Security 7 para todas las rutas /api/**. */
    private RequestMatcher apiRequestMatcher() {
        return request -> {
            String apiPath = request.getContextPath() + "/api";
            String requestUri = request.getRequestURI();
            return requestUri.equals(apiPath) || requestUri.startsWith(apiPath + "/");
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            AuthenticationSuccessHandler loginSuccessHandler,
                                            AuthenticationFailureHandler loginFailureHandler,
                                            AdminTwoFactorFilter adminTwoFactorFilter,
                                            AuthenticationEntryPoint apiAuthenticationEntryPoint,
                                            AccessDeniedHandler htmlAccessDeniedHandler) throws Exception {
        http
            // Este endpoint solo consulta recomendaciones y no modifica datos. Conservamos la
            // autorización ROLE_PACIENTE más abajo y evitamos que el token CSRF bloquee el chat.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/chat/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register/**", "/recuperar-password/**", "/doctor/**",
                                 "/especialidades/**", "/consultas", "/error", "/api/chat/**",
                                 "/css/**", "/js/**", "/img/**",
                                 "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/doctors/search").hasRole("PACIENTE")
                .requestMatchers("/api/mensajeria/**").hasAnyRole("PACIENTE", "MEDICO")
                .requestMatchers("/ws-mensajeria").hasAnyRole("PACIENTE", "MEDICO")
                .requestMatchers("/appointments/**").hasRole("PACIENTE")
                .requestMatchers("/valoraciones/**").hasRole("PACIENTE")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(loginSuccessHandler)
                .failureHandler(loginFailureHandler)
                .permitAll())
            .addFilterBefore(adminTwoFactorFilter, AuthorizationFilter.class)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authenticationException) -> {
                    if (apiRequestMatcher().matches(request)) {
                        apiAuthenticationEntryPoint.commence(request, response, authenticationException);
                    } else {
                        response.sendRedirect(request.getContextPath() + "/login");
                    }
                })
                .accessDeniedHandler(htmlAccessDeniedHandler))
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll())
            .httpBasic(basic -> basic.disable());
        return http.build();
    }
}
