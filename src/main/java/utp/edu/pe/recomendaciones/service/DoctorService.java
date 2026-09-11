package utp.edu.pe.recomendaciones.service;

import org.springframework.stereotype.Service;
import utp.edu.pe.recomendaciones.domain.Doctor;
import utp.edu.pe.recomendaciones.exception.DoctorNotFoundException;
import utp.edu.pe.recomendaciones.repository.DoctorRepository;

import java.util.List;

/* LOGICA DE NEGOCIO */

@Service 
public class DoctorService {

    private final DoctorRepository doctorRepository;

    public DoctorService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    public List<Doctor> listarTodos() {
        return doctorRepository.findAll();
    }

    public Doctor buscarPorId(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                  "No existe un medico con id: " + id));
    }
}
