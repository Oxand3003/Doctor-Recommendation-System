package utp.edu.pe.recomendaciones.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utp.edu.pe.recomendaciones.domain.Doctor;
import utp.edu.pe.recomendaciones.domain.Especialidad;
import utp.edu.pe.recomendaciones.domain.Establecimiento;
import utp.edu.pe.recomendaciones.dto.DoctorRequestDTO;
import utp.edu.pe.recomendaciones.exception.DoctorNotFoundException;
import utp.edu.pe.recomendaciones.repository.DoctorRepository;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;
import utp.edu.pe.recomendaciones.repository.EstablecimientoRepository;

/* LOGICA DE NEGOCIO */

@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final EspecialidadRepository especialidadRepository;
    private final EstablecimientoRepository establecimientoRepository;

    public DoctorService(
            DoctorRepository doctorRepository,
            EspecialidadRepository especialidadRepository,
            EstablecimientoRepository establecimientoRepository) {
        this.doctorRepository = doctorRepository;
        this.especialidadRepository = especialidadRepository;
        this.establecimientoRepository = establecimientoRepository;
    }

    @Transactional(readOnly = true)
    public List<Doctor> listarTodos() {
        return doctorRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Doctor buscarPorId(Long id) {
        return doctorRepository.findById(id)
                                .orElseThrow(() -> new DoctorNotFoundException(
                  "No existe un medico con id: " + id));
    }

    /** Crea un medico nuevo validando que el CMP no este repetido. */
    @Transactional
    public Doctor crear(DoctorRequestDTO dto) {
        if (doctorRepository.existsByCmp(dto.getCmp())) {
            throw new IllegalArgumentException(
                    "Ya existe un medico registrado con el CMP: " + dto.getCmp());
        }

        Doctor doctor = new Doctor(
                dto.getNombres(),
                dto.getApellidos(),
                dto.getCmp(),
                dto.getRating(),
                dto.getAniosExperiencia(),
                dto.getDisponible() == null ? Boolean.TRUE : dto.getDisponible(),
                resolverEspecialidad(dto.getEspecialidadId()),
                resolverEstablecimiento(dto.getEstablecimientoId()));

        return doctorRepository.save(doctor);
    }

    /** Actualiza los datos de un medico existente. */
    @Transactional
    public Doctor actualizar(Long id, DoctorRequestDTO dto) {
        Doctor doctor = buscarPorId(id);

        if (doctorRepository.existsByCmpAndIdNot(dto.getCmp(), id)) {
            throw new IllegalArgumentException(
                    "Ya existe otro medico registrado con el CMP: " + dto.getCmp());
        }

        doctor.setNombres(dto.getNombres());
        doctor.setApellidos(dto.getApellidos());
        doctor.setCmp(dto.getCmp());
        doctor.setRating(dto.getRating());
        doctor.setAniosExperiencia(dto.getAniosExperiencia());
        doctor.setDisponible(dto.getDisponible() == null ? Boolean.TRUE : dto.getDisponible());
        doctor.setEspecialidad(resolverEspecialidad(dto.getEspecialidadId()));
        doctor.setEstablecimiento(resolverEstablecimiento(dto.getEstablecimientoId()));

        return doctorRepository.save(doctor);
    }

    /** Elimina un medico por su id. */
    @Transactional
    public void eliminar(Long id) {
        Doctor doctor = buscarPorId(id);
        doctorRepository.delete(doctor);
    }

    private Especialidad resolverEspecialidad(Long especialidadId) {
        return especialidadRepository.findById(especialidadId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe una especialidad con id: " + especialidadId));
    }

    private Establecimiento resolverEstablecimiento(Long establecimientoId) {
        if (establecimientoId == null) {
            return null;
        }
        return establecimientoRepository.findById(establecimientoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe un establecimiento con id: " + establecimientoId));
    }
}
