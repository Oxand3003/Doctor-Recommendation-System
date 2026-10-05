package utp.edu.pe.recomendaciones.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import utp.edu.pe.recomendaciones.domain.Cita;
import utp.edu.pe.recomendaciones.domain.Doctor;
import utp.edu.pe.recomendaciones.domain.ModalidadCita;
import utp.edu.pe.recomendaciones.domain.EstadoUsuario;
import utp.edu.pe.recomendaciones.domain.RolUsuario;
import utp.edu.pe.recomendaciones.domain.Usuario;
import utp.edu.pe.recomendaciones.dto.CitaDTO;
import utp.edu.pe.recomendaciones.dto.CitaRequestDTO;
import utp.edu.pe.recomendaciones.dto.CitaMedicoDTO;
import utp.edu.pe.recomendaciones.exception.CitaNoDisponibleException;
import utp.edu.pe.recomendaciones.exception.CitaNotFoundException;
import utp.edu.pe.recomendaciones.exception.CitaNoAutorizadaException;
import utp.edu.pe.recomendaciones.exception.DoctorNotFoundException;
import utp.edu.pe.recomendaciones.repository.CitaRepository;
import utp.edu.pe.recomendaciones.repository.DoctorRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

@Service
public class CitaService {
  private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
  private final CitaRepository citaRepository;
  private final DoctorRepository doctorRepository;
  private final UsuarioRepository usuarioRepository;

  public CitaService(
      CitaRepository citaRepository,
      DoctorRepository doctorRepository,
      UsuarioRepository usuarioRepository) {
    this.citaRepository = citaRepository;
    this.doctorRepository = doctorRepository;
    this.usuarioRepository = usuarioRepository;
  }

  @Transactional
  public CitaDTO reservar(CitaRequestDTO request, String emailCliente) {
    Usuario cliente = usuarioRepository.findByEmailIgnoreCase(emailCliente)
        .filter(usuario -> usuario.getEstado() == EstadoUsuario.ACTIVO
            && usuario.getRol() == RolUsuario.CLIENTE)
        .orElseThrow(() -> new CitaNoAutorizadaException("Inicia sesión con una cuenta de cliente para reservar."));
    Doctor doctor = doctorRepository.findById(request.getDoctorId())
        .orElseThrow(() -> new DoctorNotFoundException(
            "No existe un medico con id: " + request.getDoctorId()));

    if (!Boolean.TRUE.equals(doctor.getDisponible())) {
      throw new CitaNoDisponibleException("El médico seleccionado no está disponible.");
    }
    if (citaRepository.existsByDoctor_IdAndFechaHora(request.getDoctorId(), request.getFechaHora())) {
      throw new CitaNoDisponibleException("Ese horario ya fue reservado. Selecciona otro horario.");
    }

    Cita cita = new Cita(
        cliente.getNombres() + " " + cliente.getApellidos(),
        cliente.getDni(),
        cliente.getTelefono(),
        request.getMotivo() == null || request.getMotivo().isBlank() ? null : request.getMotivo().trim(),
        request.getFechaHora(),
        ModalidadCita.valueOf(request.getModalidad()),
        doctor,
        cliente);

    Cita guardada = citaRepository.save(cita);
    return new CitaDTO(
        guardada.getId(),
        doctor.getId(),
        doctor.getNombres() + " " + doctor.getApellidos(),
        guardada.getFechaHora(),
        guardada.getModalidad().name(),
        guardada.getEstado().name());
  }

  @Transactional(readOnly = true)
  public CitaDTO buscarPorId(Long id, String email, boolean administrador) {
    Cita cita = citaRepository.findById(id)
        .orElseThrow(() -> new CitaNotFoundException("No existe una cita con id: " + id));
    boolean esCliente = cita.getCliente().getEmail().equalsIgnoreCase(email);
    boolean esMedico = cita.getDoctor().getUsuario() != null
        && cita.getDoctor().getUsuario().getEmail().equalsIgnoreCase(email);
    if (!administrador && !esCliente && !esMedico) {
      throw new CitaNoAutorizadaException("No tienes permiso para consultar esta cita.");
    }
    return new CitaDTO(
        cita.getId(),
        cita.getDoctor().getId(),
        cita.getDoctor().getNombres() + " " + cita.getDoctor().getApellidos(),
        cita.getFechaHora(),
        cita.getModalidad().name(),
        cita.getEstado().name());
  }

  @Transactional(readOnly = true)
  public List<String> horariosOcupados(Long doctorId, LocalDate fecha) {
    if (!doctorRepository.existsById(doctorId)) {
      throw new DoctorNotFoundException("No existe un medico con id: " + doctorId);
    }
    LocalDateTime desde = fecha.atStartOfDay();
    LocalDateTime hasta = fecha.plusDays(1).atStartOfDay();
    return citaRepository
        .findByDoctor_IdAndFechaHoraGreaterThanEqualAndFechaHoraLessThanOrderByFechaHoraAsc(
            doctorId, desde, hasta)
        .stream()
        .map(cita -> cita.getFechaHora().format(FORMATO_HORA))
        .distinct()
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CitaMedicoDTO> citasDelMedico(String email) {
    return citaRepository.findByDoctor_Usuario_EmailIgnoreCaseOrderByFechaHoraAsc(email).stream()
        .map(cita -> new CitaMedicoDTO(
            cita.getId(),
            cita.getPacienteNombre(),
            cita.getPacienteTelefono(),
            cita.getMotivo(),
            cita.getFechaHora(),
            cita.getModalidad().name(),
            cita.getEstado().name()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CitaDTO> citasDelCliente(String email) {
    return citaRepository.findByCliente_EmailIgnoreCaseOrderByFechaHoraAsc(email).stream()
        .map(cita -> new CitaDTO(
            cita.getId(),
            cita.getDoctor().getId(),
            cita.getDoctor().getNombres() + " " + cita.getDoctor().getApellidos(),
            cita.getFechaHora(),
            cita.getModalidad().name(),
            cita.getEstado().name()))
        .toList();
  }
}
