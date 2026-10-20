package utp.edu.pe.recomendaciones.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.model.Conversacion;

public interface ConversacionRepository extends JpaRepository<Conversacion, Long> {
    Optional<Conversacion> findByPaciente_IdAndMedico_Id(Long pacienteId, Long medicoId);
    List<Conversacion> findByPaciente_IdOrderByActualizadoEnDesc(Long pacienteId);
    List<Conversacion> findByMedico_IdOrderByActualizadoEnDesc(Long medicoId);
}
