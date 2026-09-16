package utp.edu.pe.recomendaciones.service;

import org.springframework.stereotype.Service;
import utp.edu.pe.recomendaciones.domain.Doctor;
import utp.edu.pe.recomendaciones.domain.Especialidad;
import utp.edu.pe.recomendaciones.domain.Establecimiento;
import utp.edu.pe.recomendaciones.dto.DoctorRequest;
import utp.edu.pe.recomendaciones.exception.DoctorNotFoundException;
import utp.edu.pe.recomendaciones.repository.DoctorRepository;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;
import utp.edu.pe.recomendaciones.repository.EstablecimientoRepository;

import java.util.List;

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

    public List<Doctor> listarTodos() {
        return doctorRepository.findAll();
    }

    public Doctor buscarPorId(Long id) {
        return doctorRepository.findById(id)
                                .orElseThrow(() -> new DoctorNotFoundException(
                  "No existe un medico con id: " + id));
    }

    public Doctor crear(DoctorRequest request) {
        validar(request);
        Especialidad especialidad = buscarEspecialidad(request.getEspecialidadId());
        Establecimiento establecimiento = buscarEstablecimiento(request.getEstablecimientoId());

        Doctor doctor = new Doctor(
                request.getNombres(),
                request.getApellidos(),
                request.getCmp(),
                request.getRating() != null ? request.getRating() : 0.0,
                request.getAniosExperiencia() != null ? request.getAniosExperiencia() : 0,
                request.getDisponible() != null ? request.getDisponible() : true,
                especialidad,
                establecimiento);

        return doctorRepository.save(doctor);
    }

    public Doctor actualizar(Long id, DoctorRequest request) {
        Doctor doctor = buscarPorId(id);
        validar(request);
        Especialidad especialidad = buscarEspecialidad(request.getEspecialidadId());
        Establecimiento establecimiento = buscarEstablecimiento(request.getEstablecimientoId());

        doctor.setNombres(request.getNombres());
        doctor.setApellidos(request.getApellidos());
        doctor.setCmp(request.getCmp());
        doctor.setRating(request.getRating() != null ? request.getRating() : doctor.getRating());
        doctor.setAniosExperiencia(
                request.getAniosExperiencia() != null ? request.getAniosExperiencia() : doctor.getAniosExperiencia());
        doctor.setDisponible(request.getDisponible() != null ? request.getDisponible() : doctor.getDisponible());
        doctor.setEspecialidad(especialidad);
        doctor.setEstablecimiento(establecimiento);

        return doctorRepository.save(doctor);
    }

    public void eliminar(Long id) {
        Doctor doctor = buscarPorId(id);
        doctorRepository.delete(doctor);
    }

    private Especialidad buscarEspecialidad(Long especialidadId) {
        if (especialidadId == null) {
            throw new IllegalArgumentException("Debe indicar la especialidad del medico.");
        }
        return especialidadRepository.findById(especialidadId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe una especialidad con id: " + especialidadId));
    }

    private Establecimiento buscarEstablecimiento(Long establecimientoId) {
        if (establecimientoId == null) {
            return null;
        }
        return establecimientoRepository.findById(establecimientoId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe un establecimiento con id: " + establecimientoId));
    }

    private void validar(DoctorRequest request) {
        if (request.getNombres() == null || request.getNombres().isBlank()) {
            throw new IllegalArgumentException("Los nombres son obligatorios.");
        }
        if (request.getApellidos() == null || request.getApellidos().isBlank()) {
            throw new IllegalArgumentException("Los apellidos son obligatorios.");
        }
        if (request.getCmp() == null || request.getCmp().isBlank()) {
            throw new IllegalArgumentException("El CMP es obligatorio.");
        }
    }
}
