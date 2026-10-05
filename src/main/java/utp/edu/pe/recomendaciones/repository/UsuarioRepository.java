package utp.edu.pe.recomendaciones.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
  Optional<Usuario> findByEmailIgnoreCase(String email);
  boolean existsByEmailIgnoreCase(String email);
  boolean existsByDni(String dni);
}
