package utp.edu.pe.recomendaciones.config;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import utp.edu.pe.recomendaciones.model.Distrito;
import utp.edu.pe.recomendaciones.model.Especialidad;
import utp.edu.pe.recomendaciones.model.Establecimiento;
import utp.edu.pe.recomendaciones.model.Rol;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.repository.DistritoRepository;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;
import utp.edu.pe.recomendaciones.repository.EstablecimientoRepository;
import utp.edu.pe.recomendaciones.repository.RolRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

/** Datos base: roles, especialidades, distritos, establecimientos y el administrador inicial. */
@Configuration
public class DataInitializer {

    @Bean
    @Order(1)
    CommandLineRunner seedData(RolRepository roles, UsuarioRepository usuarios, EspecialidadRepository especialidades,
                               DistritoRepository distritos, EstablecimientoRepository establecimientos,
                               PasswordEncoder encoder,
                               @Value("${medicerca.admin.email}") String adminEmail,
                               @Value("${medicerca.admin.password}") String adminPassword,
                               @Value("${medicerca.distritos-permitidos}") List<String> distritosPermitidos) {
        return args -> {
            for (String nombre : List.of("ADMIN", "MEDICO", "PACIENTE")) {
                if (roles.findByNombre(nombre).isEmpty()) {
                    Rol r = new Rol();
                    r.setNombre(nombre);
                    roles.save(r);
                }
            }

            for (String nombre : List.of("Cardiología", "Odontología", "Neurología", "Pediatría",
                    "Medicina General", "Oftalmología", "Traumatología", "Dermatología")) {
                if (especialidades.findByNombre(nombre).isEmpty()) {
                    Especialidad e = new Especialidad();
                    e.setNombre(nombre);
                    e.setActiva(true);
                    especialidades.save(e);
                }
            }

            for (String nombre : distritosPermitidos) {
                distritos.findByNombreAndProvincia(nombre, "Trujillo").orElseGet(() -> {
                    Distrito d = new Distrito();
                    d.setNombre(nombre);
                    d.setProvincia("Trujillo");
                    d.setDepartamento("La Libertad");
                    return distritos.save(d);
                });
            }

            java.util.Map<String, String> catalogoPrincipal = java.util.Map.ofEntries(
                    Map.entry("Trujillo", "Clínica San Pablo"),
                    Map.entry("Alto Trujillo", "Centro de Salud con camas de internamiento Alto Trujillo"),
                    Map.entry("El Porvenir", "Hospital Distrital de El Porvenir Santa Isabel"),
                    Map.entry("Florencia de Mora", "Hospital Distrital de Florencia de Mora El Esfuerzo"),
                    Map.entry("Huanchaco", "Centro Médico Huanchaco"),
                    Map.entry("La Esperanza", "Hospital Distrital de La Esperanza"),
                    Map.entry("Laredo", "CAP II Laredo"),
                    Map.entry("Moche", "Hospital Alto Moche - Walter Cruz Vilca"),
                    Map.entry("Salaverry", "CAP I Salaverry"),
                    Map.entry("Víctor Larco Herrera", "Policlínico Víctor Larco Herrera"));
            for (java.util.Map.Entry<String, String> sede : catalogoPrincipal.entrySet()) {
                Distrito distrito = distritos.findByNombreAndProvincia(sede.getKey(), "Trujillo").orElseThrow();
                boolean existe = establecimientos.findByPropietarioIsNull(org.springframework.data.domain.Sort.unsorted())
                        .stream().anyMatch(e -> sede.getValue().equals(e.getNombre())
                                && e.getDistrito().getId().equals(distrito.getId()));
                if (!existe) {
                    establecimientos.save(nuevoEstablecimiento(sede.getValue(), "Trujillo, La Libertad", distrito));
                }
            }

            // El catálogo anterior de Lima quedó obsoleto: ya no se cargan esas sedes.
            if (distritosPermitidos.isEmpty() && distritosPermitidos.size() > 0) {
            java.util.Map<String, String> catalogoAnterior = java.util.Map.ofEntries(
                    Map.entry("Trujillo", "Clínica San Pablo"),
                    Map.entry("Alto Trujillo", "Centro de Salud con camas de internamiento Alto Trujillo"),
                    Map.entry("El Porvenir", "Hospital Distrital de El Porvenir Santa Isabel"),
                    Map.entry("Florencia de Mora", "Hospital Distrital de Florencia de Mora El Esfuerzo"),
                    Map.entry("Huanchaco", "Centro Médico Huanchaco"),
                    Map.entry("La Esperanza", "Hospital Distrital de La Esperanza"),
                    Map.entry("Laredo", "CAP II Laredo"),
                    Map.entry("Moche", "Hospital Alto Moche - Walter Cruz Vilca"),
                    Map.entry("Salaverry", "CAP I Salaverry"),
                    Map.entry("Víctor Larco Herrera", "Policlínico Víctor Larco Herrera"));
            for (java.util.Map.Entry<String, String> sede : catalogoAnterior.entrySet()) {
                Distrito distrito = distritos.findByNombreAndProvincia(sede.getKey(), "Trujillo").orElseThrow();
                boolean existe = establecimientos.findByPropietarioIsNull(org.springframework.data.domain.Sort.unsorted())
                        .stream().anyMatch(e -> sede.getValue().equals(e.getNombre())
                                && e.getDistrito().getId().equals(distrito.getId()));
                if (!existe) {
                    establecimientos.save(nuevoEstablecimiento(sede.getValue(), "Trujillo, La Libertad", distrito));
                }
            }
            }

            if (distritosPermitidos.isEmpty() && distritosPermitidos.size() > 0) {
                for (String nombre : List.of("San Borja", "Surco", "Miraflores", "San Isidro", "La Molina")) {
                    Distrito d = new Distrito();
                    d.setNombre(nombre);
                    d.setProvincia("Lima");
                    d.setDepartamento("Lima");
                    distritos.save(d);
                }
                Distrito sanBorja = distritos.findByNombreAndProvincia("San Borja", "Lima").orElseThrow();
                Distrito surco = distritos.findByNombreAndProvincia("Surco", "Lima").orElseThrow();
                Distrito miraflores = distritos.findByNombreAndProvincia("Miraflores", "Lima").orElseThrow();
                establecimientos.save(nuevoEstablecimiento("Clínica San Borja", "Av. Javier Prado Este 2450", sanBorja));
                establecimientos.save(nuevoEstablecimiento("Centro Médico Surco", "Av. Primavera 1200", surco));
                establecimientos.save(nuevoEstablecimiento("Clínica Miraflores", "Av. Larco 800", miraflores));
            }

            String email = adminEmail.trim().toLowerCase();
            Rol rolAdmin = roles.findByNombre("ADMIN").orElseThrow();
            if (!adminPassword.isBlank() && !usuarios.existsByEmail(email)) {
                Usuario admin = new Usuario();
                admin.setNombres("Administrador");
                admin.setApellidos("MediCerca");
                admin.setDni("25000045");
                admin.setEmail(email);
                admin.setTelefono("900900900");
                admin.setPasswordHash(encoder.encode(adminPassword));
                admin.setEstado(EstadoUsuario.ACTIVO);
                admin.setRol(rolAdmin);
                admin.setPuedeGestionarAdministradores(true);
                usuarios.save(admin);
            } else if (!adminPassword.isBlank()) {
                usuarios.findByEmail(email).ifPresent(admin -> {
                    if (admin.getRol() != null && admin.getRol().getId().equals(rolAdmin.getId())) {
                        admin.setPasswordHash(encoder.encode(adminPassword));
                        admin.setPuedeGestionarAdministradores(true);
                        usuarios.save(admin);
                    }
                });
            }
        };
    }

    private Establecimiento nuevoEstablecimiento(String nombre, String direccion, Distrito distrito) {
        Establecimiento e = new Establecimiento();
        e.setNombre(nombre);
        e.setDireccion(direccion);
        e.setDistrito(distrito);
        return e;
    }
}
