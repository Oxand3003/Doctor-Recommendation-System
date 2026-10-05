package utp.edu.pe.recomendaciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import utp.edu.pe.recomendaciones.dto.AuthResponseDTO;
import utp.edu.pe.recomendaciones.dto.LoginDTO;
import utp.edu.pe.recomendaciones.dto.RegistroClienteDTO;
import utp.edu.pe.recomendaciones.dto.RegistroMedicoDTO;
import utp.edu.pe.recomendaciones.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService authService;
  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;

  public AuthController(
      AuthService authService,
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository) {
    this.authService = authService;
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken());
  }

  @Operation(summary = "Crea una cuenta de cliente")
  @PostMapping("/register/client")
  public ResponseEntity<AuthResponseDTO> registrarCliente(@Valid @RequestBody RegistroClienteDTO request) {
    return ResponseEntity.status(201).body(authService.registrarCliente(request));
  }

  @Operation(summary = "Crea una solicitud de cuenta médica pendiente de revisión")
  @PostMapping("/register/doctor")
  public ResponseEntity<AuthResponseDTO> registrarMedico(@Valid @RequestBody RegistroMedicoDTO request) {
    return ResponseEntity.status(201).body(authService.registrarMedico(request));
  }

  @Operation(summary = "Inicia sesión y crea una sesión segura")
  @PostMapping("/login")
  public ResponseEntity<AuthResponseDTO> login(
      @Valid @RequestBody LoginDTO request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    Authentication authentication = authenticationManager.authenticate(
        UsernamePasswordAuthenticationToken.unauthenticated(
            request.getEmail().trim().toLowerCase(Locale.ROOT), request.getPassword()));
    servletRequest.getSession(true);
    servletRequest.changeSessionId();
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, servletRequest, servletResponse);
    return ResponseEntity.ok(authService.obtenerCuenta(authentication.getName()));
  }

  @Operation(summary = "Obtiene los datos de la cuenta autenticada")
  @GetMapping("/me")
  public ResponseEntity<AuthResponseDTO> me(Authentication authentication) {
    return ResponseEntity.ok(authService.obtenerCuenta(authentication.getName()));
  }
}
