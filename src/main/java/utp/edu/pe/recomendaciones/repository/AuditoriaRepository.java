package utp.edu.pe.recomendaciones.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.model.Auditoria;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {
    List<Auditoria> findTop50ByOrderByFechaDesc();
}
