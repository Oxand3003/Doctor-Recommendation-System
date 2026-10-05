package utp.edu.pe.recomendaciones.service;

import java.time.LocalDate;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utp.edu.pe.recomendaciones.domain.Doctor;
import utp.edu.pe.recomendaciones.domain.EstadoUsuario;
import utp.edu.pe.recomendaciones.domain.EstadoVerificacionMedico;
import utp.edu.pe.recomendaciones.domain.Especialidad;
import utp.edu.pe.recomendaciones.domain.Establecimiento;
import utp.edu.pe.recomendaciones.domain.RolUsuario;
import utp.edu.pe.recomendaciones.domain.Usuario;
import utp.edu.pe.recomendaciones.dto.AuthResponseDTO;
import utp.edu.pe.recomendaciones.dto.RegistroClienteDTO;
import utp.edu.pe.recomendaciones.dto.RegistroMedicoDTO;
import utp.edu.pe.recomendaciones.exception.UsuarioNotFoundException;
import utp.edu.pe.recomendaciones.repository.DoctorRepository;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;
import utp.edu.pe.recomendaciones.repository.EstablecimientoRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

@Service
public class AuthService {
  private final UsuarioRepository usuarioRepository;
  private final DoctorRepository doctorRepository;
  private final EspecialidadRepository especialidadRepository;
  private final EstablecimientoRepository establecimientoRepository;
  private final PasswordEncoder passwordEncoder;

  public AuthService(
      UsuarioRepository usuarioRepository,
      DoctorRepository doctorRepository,
      EspecialidadRepository especialidadRepository,
      EstablecimientoRepository establecimientoRepository,
      PasswordEncoder passwordEncoder) {
    this.usuarioRepository = usuarioRepository;
    this.doctorRepository = doctorRepository;
    this.especialidadRepository = especialidadRepository;
    this.establecimientoRepository = establecimientoRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public AuthResponseDTO registrarCliente(RegistroClienteDTO request) {
    validarCuentaUnica(request.getEmail(), request.getDni());
    Usuario usuario = crearUsuario(request, RolUsuario.CLIENTE, EstadoUsuario.ACTIVO);
    Usuario guardado = usuarioRepository.save(usuario);
    return respuesta(guardado, null, "Cuenta creada. Ya puedes iniciar sesión.");
  }

  @Transactional
  public AuthResponseDTO registrarMedico(RegistroMedicoDTO request) {
    validarCuentaUnica(request.getEmail(), request.getDni());
    if (doctorRepository.existsByCmp(request.getCmp())) {
      throw new IllegalArgumentException("Ya existe una solicitud o perfil con ese CMP.");
    }
    if (request.getAnioEgreso() > LocalDate.now().getYear()) {
      throw new IllegalArgumentException("El año de egreso no puede estar en el futuro.");
    }
    Especialidad especialidad = especialidadRepository.findById(request.getEspecialidadId())
        .orElseThrow(() -> new IllegalArgumentException("La especialidad seleccionada no existe."));
    Establecimiento establecimiento = request.getEstablecimientoId() == null
        ? null
        : establecimientoRepository.findById(request.getEstablecimientoId())
            .orElseThrow(() -> new IllegalArgumentException("El establecimiento seleccionado no existe."));

    Usuario usuario = usuarioRepository.save(crearUsuario(request, RolUsuario.MEDICO, EstadoUsuario.PENDIENTE));
    Doctor doctor = new Doctor(
        request.getNombres().trim(),
        request.getApellidos().trim(),
        request.getCmp().trim().toUpperCase(Locale.ROOT),
        0.0,
        request.getAniosExperiencia(),
        false,
        especialidad,
        establecimiento);
    doctor.setRne(limpiar(request.getRne()));
    doctor.setTituloProfesional(request.getTituloProfesional().trim());
    doctor.setUniversidad(request.getUniversidad().trim());
    doctor.setAnioEgreso(request.getAnioEgreso());
    doctor.setSustentoUrl(request.getSustentoUrl().trim());
    doctor.setEstadoVerificacion(EstadoVerificacionMedico.PENDIENTE);
    doctor.setUsuario(usuario);
    Doctor guardado = doctorRepository.save(doctor);
    return respuesta(usuario, guardado.getId(), "Solicitud enviada. Podrás iniciar sesión cuando el administrador la apruebe.");
  }

  @Transactional(readOnly = true)
  public AuthResponseDTO obtenerCuenta(String email) {
    Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new UsuarioNotFoundException("No se encontró la cuenta."));
    Long doctorId = doctorRepository.findByUsuario_Id(usuario.getId()).map(Doctor::getId).orElse(null);
    return respuesta(usuario, doctorId, "Sesión activa.");
  }

  private Usuario crearUsuario(RegistroClienteDTO request, RolUsuario rol, EstadoUsuario estado) {
    return new Usuario(
        request.getNombres().trim(),
        request.getApellidos().trim(),
        request.getDni().trim(),
        normalizarEmail(request.getEmail()),
        passwordEncoder.encode(request.getPassword()),
        request.getTelefono().trim(),
        rol,
        estado);
  }

  private void validarCuentaUnica(String email, String dni) {
    if (usuarioRepository.existsByEmailIgnoreCase(email.trim())) {
      throw new IllegalArgumentException("Ya existe una cuenta con ese correo electrónico.");
    }
    if (usuarioRepository.existsByDni(dni.trim())) {
      throw new IllegalArgumentException("Ya existe una cuenta con ese DNI.");
    }
  }

  private AuthResponseDTO respuesta(Usuario usuario, Long doctorId, String mensaje) {
    String estado = usuario.getEstado().name();
    if (doctorId != null) {
      estado = doctorRepository.findById(doctorId)
          .map(doctor -> doctor.getEstadoVerificacion().name()).orElse(estado);
    }
    return new AuthResponseDTO(
        usuario.getId(), usuario.getNombres(), usuario.getApellidos(), usuario.getEmail(),
        usuario.getDni(), usuario.getTelefono(),
        usuario.getRol().name(), estado, mensaje, doctorId);
  }

  private String normalizarEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String limpiar(String valor) {
    return valor == null || valor.isBlank() ? null : valor.trim();
  }
}
