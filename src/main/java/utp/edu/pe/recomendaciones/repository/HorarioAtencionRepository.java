package utp.edu.pe.recomendaciones.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.model.HorarioAtencion;

public interface HorarioAtencionRepository extends JpaRepository<HorarioAtencion, Long> {
    List<HorarioAtencion> findByMedicoIdAndActivoTrue(Long medicoId);

    List<HorarioAtencion> findByMedicoIdOrderByDiaSemanaAscHoraInicioAsc(Long medicoId);

    List<HorarioAtencion> findByMedicoIdAndDiaSemana(Long medicoId, int diaSemana);

    Optional<HorarioAtencion> findByIdAndMedicoId(Long id, Long medicoId);

    long countByMedicoId(Long medicoId);
}
