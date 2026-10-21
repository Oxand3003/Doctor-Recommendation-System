package utp.edu.pe.recomendaciones.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.model.Consulta;
import utp.edu.pe.recomendaciones.model.enums.EstadoConsulta;

public interface ConsultaRepository extends JpaRepository<Consulta, Long> {
    List<Consulta> findByEstadoOrderByFechaCreacionDesc(EstadoConsulta estado);

    List<Consulta> findTop100ByOrderByFechaCreacionDesc();

    /** Lista del panel de administración. */
    List<Consulta> findTop500ByOrderByFechaCreacionDesc();

    long countByEstado(EstadoConsulta estado);

    /** Para frenar el envío masivo desde un mismo correo. */
    long countByCorreoIgnoreCaseAndFechaCreacionAfter(String correo, LocalDateTime desde);
}
