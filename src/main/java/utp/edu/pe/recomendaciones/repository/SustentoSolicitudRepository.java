package utp.edu.pe.recomendaciones.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import utp.edu.pe.recomendaciones.model.SustentoSolicitud;

public interface SustentoSolicitudRepository extends JpaRepository<SustentoSolicitud, Long> {
    Optional<SustentoSolicitud> findBySolicitudId(Long solicitudId);
}
