package utp.edu.pe.recomendaciones.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import utp.edu.pe.recomendaciones.domain.Doctor;
import utp.edu.pe.recomendaciones.domain.Especialidad;
import utp.edu.pe.recomendaciones.domain.Establecimiento;
import utp.edu.pe.recomendaciones.domain.EstadoUsuario;
import utp.edu.pe.recomendaciones.domain.RolUsuario;
import utp.edu.pe.recomendaciones.domain.Usuario;
import utp.edu.pe.recomendaciones.repository.DoctorRepository;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;
import utp.edu.pe.recomendaciones.repository.EstablecimientoRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

/** Carga un catálogo de demostración solo al inicializar una base vacía. */
@Component
public class DataInitializer implements CommandLineRunner {

  private final DoctorRepository doctorRepository;
  private final EspecialidadRepository especialidadRepository;
  private final EstablecimientoRepository establecimientoRepository;
  private final UsuarioRepository usuarioRepository;
  private final PasswordEncoder passwordEncoder;
  private final String adminEmail;
  private final String adminPassword;

  public DataInitializer(
      DoctorRepository doctorRepository,
      EspecialidadRepository especialidadRepository,
      EstablecimientoRepository establecimientoRepository,
      UsuarioRepository usuarioRepository,
      PasswordEncoder passwordEncoder,
      @Value("${medicerca.admin.email}") String adminEmail,
      @Value("${medicerca.admin.password}") String adminPassword) {
    this.doctorRepository = doctorRepository;
    this.especialidadRepository = especialidadRepository;
    this.establecimientoRepository = establecimientoRepository;
    this.usuarioRepository = usuarioRepository;
    this.passwordEncoder = passwordEncoder;
    this.adminEmail = adminEmail;
    this.adminPassword = adminPassword;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (!usuarioRepository.existsByEmailIgnoreCase(adminEmail)) {
      usuarioRepository.save(new Usuario(
          "Administrador",
          "MediCerca",
          "00000000",
          adminEmail.trim().toLowerCase(),
          passwordEncoder.encode(adminPassword),
          "000000000",
          RolUsuario.ADMINISTRADOR,
          EstadoUsuario.ACTIVO));
    }

    if (especialidadRepository.count() > 0) {
      return;
    }

    List<Especialidad> especialidades = especialidadRepository.saveAll(List.of(
        new Especialidad("Cardiología"),
        new Especialidad("Dermatología"),
        new Especialidad("Pediatría"),
        new Especialidad("Medicina General"),
        new Especialidad("Odontología"),
        new Especialidad("Neurología"),
        new Especialidad("Oftalmología"),
        new Especialidad("Traumatología")));

    List<Establecimiento> establecimientos = establecimientoRepository.saveAll(List.of(
        new Establecimiento("Clínica San Felipe", "Av. Gregorio Escobedo 650", "Jesús María"),
        new Establecimiento("Clínica Internacional", "Av. Guardia Civil 385", "San Borja"),
        new Establecimiento("Centro Médico Salud Norte", "Av. Carlos Izaguirre 920", "Los Olivos")));

    doctorRepository.saveAll(List.of(
        doctor("María", "Fernández Torres", "CMP10001", 4.9, 15, true, especialidades.get(0), establecimientos.get(0)),
        doctor("Carlos", "Ramírez Soto", "CMP10002", 4.7, 20, true, especialidades.get(0), establecimientos.get(1)),
        doctor("Ana", "Gómez Pérez", "CMP10003", 4.8, 10, true, especialidades.get(1), establecimientos.get(1)),
        doctor("Luis", "Castillo Vega", "CMP10004", 4.6, 12, false, especialidades.get(1), establecimientos.get(2)),
        doctor("Sofía", "Mendoza Ruiz", "CMP10005", 4.9, 8, true, especialidades.get(2), establecimientos.get(0)),
        doctor("Jorge", "Vargas León", "CMP10006", 4.5, 18, true, especialidades.get(3), establecimientos.get(2)),
        doctor("Patricia", "Ríos Salazar", "CMP10007", 4.7, 11, true, especialidades.get(4), establecimientos.get(1)),
        doctor("Diego", "Herrera Lazo", "CMP10008", 4.6, 14, true, especialidades.get(5), establecimientos.get(0)),
        doctor("Carmen", "Vega Núñez", "CMP10009", 4.8, 9, true, especialidades.get(6), establecimientos.get(2)),
        doctor("Ricardo", "Salas Ponce", "CMP10010", 4.5, 16, true, especialidades.get(7), establecimientos.get(1))));
  }

  private Doctor doctor(
      String nombres,
      String apellidos,
      String cmp,
      double rating,
      int experiencia,
      boolean disponible,
      Especialidad especialidad,
      Establecimiento establecimiento) {
    return new Doctor(nombres, apellidos, cmp, rating, experiencia, disponible, especialidad, establecimiento);
  }
}
