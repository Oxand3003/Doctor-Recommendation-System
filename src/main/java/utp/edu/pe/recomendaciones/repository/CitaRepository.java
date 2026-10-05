package utp.edu.pe.recomendaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import utp.edu.pe.recomendaciones.domain.Cita;

public interface CitaRepository extends JpaRepository<Cita, Long> {
  boolean existsByDoctor_IdAndFechaHora(Long doctorId, java.time.LocalDateTime fechaHora);

  List<Cita> findByDoctor_IdAndFechaHoraGreaterThanEqualAndFechaHoraLessThanOrderByFechaHoraAsc(
      Long doctorId, LocalDateTime desde, LocalDateTime hasta);

  List<Cita> findByDoctor_Usuario_EmailIgnoreCaseOrderByFechaHoraAsc(String email);

  List<Cita> findByCliente_EmailIgnoreCaseOrderByFechaHoraAsc(String email);
}
