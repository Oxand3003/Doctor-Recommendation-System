package utp.edu.pe.recomendaciones.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.model.Distrito;

public interface DistritoRepository extends JpaRepository<Distrito, Long> {
    Optional<Distrito> findByNombreAndProvincia(String nombre, String provincia);
}
