package utp.edu.pe.recomendaciones.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import utp.edu.pe.recomendaciones.model.SolicitudDesactivacionAdmin;
import utp.edu.pe.recomendaciones.model.enums.EstadoDesactivacionAdmin;

public interface SolicitudDesactivacionAdminRepository extends JpaRepository<SolicitudDesactivacionAdmin, Long> {

    List<SolicitudDesactivacionAdmin> findByEstadoOrderByCreadoEnDesc(EstadoDesactivacionAdmin estado);

    List<SolicitudDesactivacionAdmin> findTop10ByEstadoNotOrderByResueltoEnDesc(EstadoDesactivacionAdmin estado);

    boolean existsByObjetivoIdAndEstado(Long objetivoId, EstadoDesactivacionAdmin estado);

    List<SolicitudDesactivacionAdmin> findByObjetivoIdAndEstado(Long objetivoId, EstadoDesactivacionAdmin estado);

    /** Bloquea la solicitud (SELECT ... FOR UPDATE): dos votos simultáneos se atienden uno detrás de otro. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudDesactivacionAdmin s where s.id = :id")
    Optional<SolicitudDesactivacionAdmin> bloquearPorId(@Param("id") Long id);
}
