package utp.edu.pe.recomendaciones.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.domain.Especialidad;

/**
 * MODULO DE DOMINIO (Integrante 1)
 * Acceso a datos de Especialidad.
 */
public interface EspecialidadRepository extends JpaRepository<Especialidad, Long> {
}
