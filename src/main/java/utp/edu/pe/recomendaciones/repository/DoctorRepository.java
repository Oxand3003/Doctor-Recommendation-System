package utp.edu.pe.recomendaciones.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import utp.edu.pe.recomendaciones.domain.Doctor;
import utp.edu.pe.recomendaciones.domain.EstadoVerificacionMedico;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

  List<Doctor> findByDisponibleTrue();

  List<Doctor> findByEspecialidad_NombreIgnoreCaseAndDisponibleTrue(String especialidad);

  boolean existsByCmp(String cmp);

  boolean existsByCmpAndIdNot(String cmp, Long id);

  List<Doctor> findByEstadoVerificacionOrderByIdAsc(EstadoVerificacionMedico estado);

  Optional<Doctor> findByUsuario_Id(Long usuarioId);
}
