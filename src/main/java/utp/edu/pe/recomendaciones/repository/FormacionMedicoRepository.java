package utp.edu.pe.recomendaciones.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.model.FormacionMedico;

public interface FormacionMedicoRepository extends JpaRepository<FormacionMedico, Long> {
    List<FormacionMedico> findByMedicoId(Long medicoId);

    Optional<FormacionMedico> findByIdAndMedicoId(Long id, Long medicoId);

    long countByMedicoId(Long medicoId);
}
