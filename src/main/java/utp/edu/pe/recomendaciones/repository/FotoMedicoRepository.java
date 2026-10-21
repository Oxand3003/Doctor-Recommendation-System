package utp.edu.pe.recomendaciones.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import utp.edu.pe.recomendaciones.model.FotoMedico;
import utp.edu.pe.recomendaciones.model.enums.TipoFoto;

public interface FotoMedicoRepository extends JpaRepository<FotoMedico, Long> {

    List<FotoMedico> findByMedicoIdAndTipoOrderByCreadoEnDesc(Long medicoId, TipoFoto tipo);

    Optional<FotoMedico> findFirstByMedicoIdAndTipoOrderByCreadoEnDesc(Long medicoId, TipoFoto tipo);

    Optional<FotoMedico> findByIdAndMedicoId(Long id, Long medicoId);

    long countByMedicoIdAndTipo(Long medicoId, TipoFoto tipo);
}
