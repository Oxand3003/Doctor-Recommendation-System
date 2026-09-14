package utp.edu.pe.recomendaciones.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.domain.Establecimiento;

/**
 * MODULO DE DOMINIO (Integrante 1)
 * Acceso a datos de Establecimiento.
 */
public interface EstablecimientoRepository extends JpaRepository<Establecimiento, Long> {
}