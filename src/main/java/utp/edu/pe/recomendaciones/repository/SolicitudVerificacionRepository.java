package utp.edu.pe.recomendaciones.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import utp.edu.pe.recomendaciones.model.SolicitudVerificacion;
import utp.edu.pe.recomendaciones.model.enums.EstadoSolicitud;

public interface SolicitudVerificacionRepository extends JpaRepository<SolicitudVerificacion, Long> {
    List<SolicitudVerificacion> findByEstadoOrderByFechaSolicitudDesc(EstadoSolicitud estado);
    boolean existsByMedicoIdAndEstado(Long medicoId, EstadoSolicitud estado);

    /**
     * Lee la solicitud bloqueando su fila hasta terminar la transacción. Así, si el administrador hace doble
     * clic (o dos administradores deciden a la vez), la segunda petición espera, ve que la solicitud ya fue
     * revisada y no genera una segunda decisión ni un segundo correo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SolicitudVerificacion s where s.id = :id")
    Optional<SolicitudVerificacion> buscarParaRevisar(@Param("id") Long id);
}
