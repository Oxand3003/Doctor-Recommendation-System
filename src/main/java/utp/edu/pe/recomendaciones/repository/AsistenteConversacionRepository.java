package utp.edu.pe.recomendaciones.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.model.AsistenteConversacion;

public interface AsistenteConversacionRepository extends JpaRepository<AsistenteConversacion, Long> {
    Optional<AsistenteConversacion> findByUsuarioId(Long usuarioId);

    long deleteByUsuarioId(Long usuarioId);
}
