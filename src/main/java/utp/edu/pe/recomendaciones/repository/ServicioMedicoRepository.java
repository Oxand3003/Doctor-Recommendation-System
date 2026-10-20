package utp.edu.pe.recomendaciones.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.model.ServicioMedico;

public interface ServicioMedicoRepository extends JpaRepository<ServicioMedico, Long> {
    List<ServicioMedico> findByMedicoIdAndActivoTrue(Long medicoId);
}
