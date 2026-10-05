package utp.edu.pe.recomendaciones.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utp.edu.pe.recomendaciones.domain.Doctor;
import utp.edu.pe.recomendaciones.domain.EstadoUsuario;
import utp.edu.pe.recomendaciones.domain.EstadoVerificacionMedico;
import utp.edu.pe.recomendaciones.dto.SolicitudMedicoDTO;
import utp.edu.pe.recomendaciones.exception.DoctorNotFoundException;
import utp.edu.pe.recomendaciones.repository.DoctorRepository;

@Service
public class AdminService {
  private final DoctorRepository doctorRepository;

  public AdminService(DoctorRepository doctorRepository) {
    this.doctorRepository = doctorRepository;
  }

  @Transactional(readOnly = true)
  public List<SolicitudMedicoDTO> solicitudesPendientes() {
    return doctorRepository.findByEstadoVerificacionOrderByIdAsc(EstadoVerificacionMedico.PENDIENTE)
        .stream().map(this::aDTO).toList();
  }

  @Transactional
  public SolicitudMedicoDTO aprobar(Long doctorId) {
    Doctor doctor = buscarSolicitudPendiente(doctorId);
    doctor.setEstadoVerificacion(EstadoVerificacionMedico.APROBADO);
    doctor.setMotivoRechazo(null);
    doctor.setDisponible(true);
    doctor.getUsuario().setEstado(EstadoUsuario.ACTIVO);
    return aDTO(doctor);
  }

  @Transactional
  public SolicitudMedicoDTO rechazar(Long doctorId, String motivo) {
    Doctor doctor = buscarSolicitudPendiente(doctorId);
    doctor.setEstadoVerificacion(EstadoVerificacionMedico.RECHAZADO);
    doctor.setMotivoRechazo(motivo.trim());
    doctor.setDisponible(false);
    doctor.getUsuario().setEstado(EstadoUsuario.RECHAZADO);
    return aDTO(doctor);
  }

  private Doctor buscarSolicitudPendiente(Long doctorId) {
    Doctor doctor = doctorRepository.findById(doctorId)
        .orElseThrow(() -> new DoctorNotFoundException("No existe una solicitud con id: " + doctorId));
    if (doctor.getEstadoVerificacion() != EstadoVerificacionMedico.PENDIENTE) {
      throw new IllegalArgumentException("Esta solicitud ya fue revisada.");
    }
    return doctor;
  }

  private SolicitudMedicoDTO aDTO(Doctor doctor) {
    var usuario = doctor.getUsuario();
    return new SolicitudMedicoDTO(
        doctor.getId(),
        usuario.getId(),
        usuario.getNombres(),
        usuario.getApellidos(),
        usuario.getDni(),
        usuario.getEmail(),
        usuario.getTelefono(),
        doctor.getCmp(),
        doctor.getRne(),
        doctor.getTituloProfesional(),
        doctor.getUniversidad(),
        doctor.getAnioEgreso(),
        doctor.getAniosExperiencia(),
        doctor.getEspecialidad().getNombre(),
        doctor.getEstablecimiento() == null ? null : doctor.getEstablecimiento().getNombre(),
        doctor.getSustentoUrl(),
        doctor.getEstadoVerificacion().name());
  }
}
