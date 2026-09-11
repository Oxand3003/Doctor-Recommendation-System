package utp.edu.pe.recomendaciones.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import utp.edu.pe.recomendaciones.domain.Doctor;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

  List<Doctor> findByEspecialidad_NombreIgnoreCaseAndDisponibleTrue(String especialidad);
}
