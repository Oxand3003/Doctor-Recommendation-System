package utp.edu.pe.recomendaciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import java.util.List;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {
  @Bean
  AuthenticationManager authenticationManager(AuthenticationProvider authenticationProvider) {
    return new ProviderManager(List.of(authenticationProvider));
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      AuthenticationProvider authenticationProvider,
      SecurityContextRepository securityContextRepository) throws Exception {
    http
        .authenticationProvider(authenticationProvider)
        .securityContext(context -> context.securityContextRepository(securityContextRepository))
        .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/register/**").permitAll()
            .requestMatchers("/api/v1/auth/me").authenticated()
            .requestMatchers("/api/v1/admin/**").hasRole("ADMINISTRADOR")
            .requestMatchers("/api/v1/doctor/**").hasRole("MEDICO")
            .requestMatchers("/api/v1/client/**").hasRole("CLIENTE")
            .requestMatchers("/api/v1/catalog/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/v1/appointments").hasRole("CLIENTE")
            .requestMatchers(HttpMethod.GET, "/api/v1/appointments/availability").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/appointments/*")
                .hasAnyRole("CLIENTE", "MEDICO", "ADMINISTRADOR")
            .requestMatchers(HttpMethod.GET, "/api/v1/doctors", "/api/v1/doctors/*", "/api/v1/doctors/recomendar")
                .permitAll()
            .requestMatchers("/api/v1/doctors/**").hasRole("ADMINISTRADOR")
            .requestMatchers("/api/v1/**").authenticated()
            .anyRequest().permitAll())
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, exception) -> {
              response.setStatus(401);
              response.setContentType("application/json");
              response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Inicia sesión para continuar.\"}");
            })
            .accessDeniedHandler((request, response, exception) -> {
              response.setStatus(403);
              response.setContentType("application/json");
              response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"No tienes permiso para esta operación.\"}");
            }))
        .logout(logout -> logout
            .logoutUrl("/api/v1/auth/logout")
            .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204))
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID", "XSRF-TOKEN"));
    return http.build();
  }
}
