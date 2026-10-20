package utp.edu.pe.recomendaciones.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.model.Valoracion;

public interface ValoracionRepository extends JpaRepository<Valoracion, Long> {
    List<Valoracion> findByMedicoIdOrderByCreadaEnDesc(Long medicoId);
    boolean existsByCitaId(Long citaId);

    Optional<Valoracion> findByIdAndMedicoId(Long id, Long medicoId);

    /** Lo que ve el público: solo las reseñas que el médico dejó visibles. */
    List<Valoracion> findByMedicoIdAndVisiblePublicoTrueOrderByCreadaEnDesc(Long medicoId);
}
