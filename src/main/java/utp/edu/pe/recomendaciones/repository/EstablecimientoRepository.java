package utp.edu.pe.recomendaciones.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.model.Establecimiento;

public interface EstablecimientoRepository extends JpaRepository<Establecimiento, Long> {
    List<Establecimiento> findByDistritoId(Long distritoId);

    /** Sedes del catálogo (sin dueño). */
    List<Establecimiento> findByPropietarioIsNull(Sort sort);

    List<Establecimiento> findByPropietarioIdOrderByNombreAsc(Long medicoId);

    Optional<Establecimiento> findByIdAndPropietarioId(Long id, Long medicoId);

    long countByPropietarioId(Long medicoId);
}
