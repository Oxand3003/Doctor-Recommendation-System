package utp.edu.pe.recomendaciones.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

@Service
public class UsuarioDetailsService implements UserDetailsService {
  private final UsuarioRepository usuarioRepository;

  public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
    this.usuarioRepository = usuarioRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    var usuario = usuarioRepository.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas."));
    return User.withUsername(usuario.getEmail())
        .password(usuario.getPasswordHash())
        .roles(usuario.getRol().name())
        .disabled(usuario.getEstado() != utp.edu.pe.recomendaciones.domain.EstadoUsuario.ACTIVO)
        .build();
  }
}
