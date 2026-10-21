package utp.edu.pe.recomendaciones.repository;



import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;

public interface MedicoRepository extends JpaRepository<Medico, Long> {

    Optional<Medico> findByUsuarioId(Long usuarioId);

    boolean existsByCmp(String cmp);

    Optional<Medico> findByCmp(String cmp);

    List<Medico> findByEspecialidadIdOrderByRatingPromedioDesc(Long especialidadId);

    @EntityGraph(attributePaths = {"usuario", "especialidad", "establecimiento", "establecimiento.distrito"})
    List<Medico> findByUsuarioEstadoOrderByRatingPromedioDesc(EstadoUsuario estado);

    @EntityGraph(attributePaths = {"usuario", "especialidad", "establecimiento", "establecimiento.distrito"})
    Page<Medico> findByEspecialidadIdAndUsuarioEstado(Long especialidadId, EstadoUsuario estado, Pageable pageable);

    // solo medicos cuyo usuario está en el estado indicado (ACTIVO)
    /** Cuántos médicos tienen esta sede como principal (para no borrar una sede en uso). */
    long countByEstablecimientoId(Long establecimientoId);

    Optional<Medico> findByIdAndUsuarioEstado(Long id, EstadoUsuario estado);

    /**
     * Lee al médico con bloqueo de escritura (SELECT ... FOR UPDATE). Las reservas lo usan para que dos
     * pacientes que piden el mismo turno al mismo tiempo se atiendan uno detrás de otro: el segundo ya ve
     * la cita del primero y recibe "turno ocupado". Solo dentro de una transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Medico m where m.id = :id")
    Optional<Medico> bloquearPorId(@Param("id") Long id);
}
