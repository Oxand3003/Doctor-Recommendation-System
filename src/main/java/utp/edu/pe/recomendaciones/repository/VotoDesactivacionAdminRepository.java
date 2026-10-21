package utp.edu.pe.recomendaciones.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import utp.edu.pe.recomendaciones.model.VotoDesactivacionAdmin;

public interface VotoDesactivacionAdminRepository extends JpaRepository<VotoDesactivacionAdmin, Long> {

    List<VotoDesactivacionAdmin> findBySolicitudIdOrderByCreadoEnAsc(Long solicitudId);

    boolean existsBySolicitudIdAndVotanteId(Long solicitudId, Long votanteId);

    long countBySolicitudId(Long solicitudId);
}
