package utp.edu.pe.recomendaciones.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.model.Rol;

public interface RolRepository extends JpaRepository<Rol, Long> {
    Optional<Rol> findByNombre(String nombre);
}
