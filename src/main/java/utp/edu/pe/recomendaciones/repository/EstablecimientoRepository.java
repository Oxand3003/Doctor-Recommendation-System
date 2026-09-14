package utp.edu.pe.recomendaciones.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.salud.recomendaciones.domain.EstablecimientoSalud;

/**
 * MODULO DE DOMINIO (Integrante 1)
 * Acceso a datos de EstablecimientoSalud.
 */
public interface EstablecimientoRepository extends JpaRepository<EstablecimientoSalud, Long> {
}