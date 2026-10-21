package utp.edu.pe.recomendaciones.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import utp.edu.pe.recomendaciones.model.Cita;
import utp.edu.pe.recomendaciones.model.enums.EstadoCita;

/**
 * Consultas de SOLO LECTURA para el dashboard y la agenda del médico. Es un repositorio aparte para no
 * tocar CitaRepository (que usan la reserva y el perfil público). Todas las consultas filtran por el id
 * del médico de la sesión: un médico nunca puede leer citas de otro.
 */
public interface AgendaCitaRepository extends JpaRepository<Cita, Long> {

    /** Pacientes distintos con al menos una cita COMPLETADA con este médico. */
    @Query("select count(distinct c.paciente.id) from Cita c where c.medico.id = :medicoId and c.estado = :estado")
    long contarPacientesDistintos(@Param("medicoId") Long medicoId, @Param("estado") EstadoCita estado);

    @Query("select count(c) from Cita c where c.medico.id = :medicoId and c.estado = :estado "
            + "and c.fechaHoraInicio >= :desde")
    long contarDesde(@Param("medicoId") Long medicoId, @Param("estado") EstadoCita estado,
                     @Param("desde") LocalDateTime desde);

    @Query("select c from Cita c join fetch c.paciente where c.medico.id = :medicoId "
            + "and c.estado in :estados and c.fechaHoraInicio >= :desde order by c.fechaHoraInicio")
    List<Cita> proximas(@Param("medicoId") Long medicoId, @Param("estados") Collection<EstadoCita> estados,
                        @Param("desde") LocalDateTime desde, Pageable pagina);

    /** Citas con inicio en [desde, hasta). */
    @Query("select c from Cita c join fetch c.paciente where c.medico.id = :medicoId "
            + "and c.fechaHoraInicio >= :desde and c.fechaHoraInicio < :hasta order by c.fechaHoraInicio")
    List<Cita> entre(@Param("medicoId") Long medicoId, @Param("desde") LocalDateTime desde,
                     @Param("hasta") LocalDateTime hasta);

    @Query("select c from Cita c join fetch c.paciente where c.medico.id = :medicoId "
            + "and c.estado in :estados and c.fechaHoraInicio >= :desde and c.fechaHoraInicio < :hasta "
            + "order by c.fechaHoraInicio")
    List<Cita> entreEstados(@Param("medicoId") Long medicoId, @Param("estados") Collection<EstadoCita> estados,
                            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("select c from Cita c join fetch c.paciente where c.id = :id and c.medico.id = :medicoId")
    Optional<Cita> findByIdAndMedicoId(@Param("id") Long id, @Param("medicoId") Long medicoId);
}
