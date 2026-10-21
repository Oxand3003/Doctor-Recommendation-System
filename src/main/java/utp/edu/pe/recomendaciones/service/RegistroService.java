package utp.edu.pe.recomendaciones.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.dto.OpcionView;
import utp.edu.pe.recomendaciones.dto.RegistroMedicoForm;
import utp.edu.pe.recomendaciones.dto.RegistroPacienteForm;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Especialidad;
import utp.edu.pe.recomendaciones.model.Establecimiento;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.Rol;
import utp.edu.pe.recomendaciones.model.SolicitudVerificacion;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.EstadoSolicitud;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;
import utp.edu.pe.recomendaciones.repository.EstablecimientoRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.repository.RolRepository;
import utp.edu.pe.recomendaciones.repository.SolicitudVerificacionRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

@Service
public class RegistroService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern DNI = Pattern.compile("^[0-9]{8}$");
    private static final Pattern TEL = Pattern.compile("^[0-9 +()\\-]{6,30}$");
    private static final Pattern CMP = Pattern.compile("^[A-Za-z0-9-]{4,20}$");

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final MedicoRepository medicos;
    private final EspecialidadRepository especialidades;
    private final EstablecimientoRepository establecimientos;
    private final SolicitudVerificacionRepository solicitudes;
    private final PasswordEncoder encoder;

    public RegistroService(UsuarioRepository usuarios, RolRepository roles, MedicoRepository medicos,
                           EspecialidadRepository especialidades, EstablecimientoRepository establecimientos,
                           SolicitudVerificacionRepository solicitudes, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.medicos = medicos;
        this.especialidades = especialidades;
        this.establecimientos = establecimientos;
        this.solicitudes = solicitudes;
        this.encoder = encoder;
    }

    // ------------------------------------------------------------------
    // PACIENTE: cuenta ACTIVA al instante
    // ------------------------------------------------------------------
    @Transactional
    public void registrarPaciente(RegistroPacienteForm f) {
        Persona p = validarPersona(f.nombres(), f.apellidos(), f.dni(), f.telefono(), f.email(), f.password());
        if (usuarios.existsByEmail(p.email())) {
            throw new NegocioException("Ya existe una cuenta con ese correo electrónico.");
        }
        if (usuarios.existsByDni(p.dni())) {
            throw new NegocioException("Ya existe una cuenta con ese DNI.");
        }
        crearUsuario(p, "PACIENTE", EstadoUsuario.ACTIVO);
    }

    // ------------------------------------------------------------------
    // MÉDICO: cuenta PENDIENTE + solicitud PENDIENTE (el admin decide)
    // ------------------------------------------------------------------
    @Transactional
    public void registrarMedico(RegistroMedicoForm f) {
        Persona p = validarPersona(f.nombres(), f.apellidos(), f.dni(), f.telefono(), f.email(), f.password());

        String cmp = limpiar(f.cmp()).toUpperCase();
        if (!CMP.matcher(cmp).matches()) {
            throw new NegocioException("El número CMP debe tener entre 4 y 20 letras, números o guiones.");
        }
        String rne = limpiar(f.rne());
        if (rne.length() > 20) {
            throw new NegocioException("El RNE no puede superar 20 caracteres.");
        }
        String titulo = exigir(f.tituloProfesional(), "El título profesional", 150);
        String universidad = exigir(f.universidad(), "La universidad", 180);

        int anioActual = Year.now().getValue();
        if (f.anioEgreso() == null || f.anioEgreso() < 1950 || f.anioEgreso() > anioActual) {
            throw new NegocioException("El año de egreso debe estar entre 1950 y " + anioActual + ".");
        }
        if (f.aniosExperiencia() == null || f.aniosExperiencia() < 0 || f.aniosExperiencia() > 80) {
            throw new NegocioException("Los años de experiencia deben estar entre 0 y 80.");
        }

        if (f.especialidadId() == null) {
            throw new NegocioException("Selecciona una especialidad.");
        }
        Especialidad esp = especialidades.findById(f.especialidadId())
                .filter(Especialidad::isActiva)
                .orElseThrow(() -> new NegocioException("La especialidad seleccionada no es válida."));

        Establecimiento est = null;
        if (f.establecimientoId() != null) {
            est = establecimientos.findById(f.establecimientoId())
                    .orElseThrow(() -> new NegocioException("El establecimiento seleccionado no es válido."));
        }

        String sustento = validarUrl(f.sustentoUrl());

        // ¿Es un médico rechazado que vuelve a enviar su solicitud?
        Optional<Usuario> existente = usuarios.findByEmail(p.email());
        if (existente.isPresent()) {
            reenviar(existente.get(), p, cmp, rne, titulo, universidad, f, esp, est, sustento);
            return;
        }

        if (usuarios.existsByDni(p.dni())) {
            throw new NegocioException("Ya existe una cuenta con ese DNI.");
        }
        if (medicos.existsByCmp(cmp)) {
            throw new NegocioException("Ya existe un médico registrado con ese número CMP.");
        }

        Usuario u = crearUsuario(p, "MEDICO", EstadoUsuario.PENDIENTE);

        Medico m = new Medico();
        m.setUsuario(u);
        m.setCmp(cmp);
        m.setRne(rne.isEmpty() ? null : rne);
        m.setAniosExperiencia(f.aniosExperiencia());
        m.setTituloProfesional(titulo);
        m.setUniversidad(universidad);
        m.setAnioEgreso(f.anioEgreso());
        m.setEspecialidad(esp);
        m.setEstablecimiento(est);
        m = medicos.save(m);

        crearSolicitud(m, sustento);
    }

    private void reenviar(Usuario u, Persona p, String cmp, String rne, String titulo, String universidad,
                          RegistroMedicoForm f, Especialidad esp, Establecimiento est, String sustento) {
        Medico m = medicos.findByUsuarioId(u.getId()).orElse(null);
        boolean puede = m != null
                && "MEDICO".equals(u.getRol().getNombre())
                && u.getEstado() == EstadoUsuario.PENDIENTE
                && encoder.matches(p.password(), u.getPasswordHash())
                && solicitudes.existsByMedicoIdAndEstado(m.getId(), EstadoSolicitud.RECHAZADA)
                && !solicitudes.existsByMedicoIdAndEstado(m.getId(), EstadoSolicitud.PENDIENTE);
        if (!puede) {
            throw new NegocioException("Ya existe una cuenta con ese correo electrónico.");
        }
        if (!u.getDni().equals(p.dni())) {
            throw new NegocioException("El DNI no coincide con el de tu solicitud anterior.");
        }
        Optional<Medico> otro = medicos.findByCmp(cmp);
        if (otro.isPresent() && !otro.get().getId().equals(m.getId())) {
            throw new NegocioException("Ya existe un médico registrado con ese número CMP.");
        }

        u.setNombres(p.nombres());
        u.setApellidos(p.apellidos());
        u.setTelefono(p.telefono());
        usuarios.save(u);

        m.setCmp(cmp);
        m.setRne(rne.isEmpty() ? null : rne);
        m.setAniosExperiencia(f.aniosExperiencia());
        m.setTituloProfesional(titulo);
        m.setUniversidad(universidad);
        m.setAnioEgreso(f.anioEgreso());
        m.setEspecialidad(esp);
        m.setEstablecimiento(est);
        medicos.save(m);

        crearSolicitud(m, sustento);
    }

    // ------------------------------------------------------------------
    // Opciones para los <select> del formulario de médico
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<OpcionView> especialidades() {
        return especialidades.findByActivaTrueOrderByNombreAsc().stream()
                .map(e -> new OpcionView(e.getId(), e.getNombre()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OpcionView> establecimientos() {
        return establecimientos.findByPropietarioIsNull(Sort.by("nombre")).stream()
                .map(e -> new OpcionView(e.getId(), e.getNombre() + " — " + e.getDistrito().getNombre()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------
    private record Persona(String nombres, String apellidos, String dni, String telefono,
                           String email, String password) { }

    private Persona validarPersona(String nombres, String apellidos, String dni, String telefono,
                                   String email, String password) {
        String n = exigir(nombres, "Los nombres", 100);
        String a = exigir(apellidos, "Los apellidos", 100);
        String d = limpiar(dni);
        if (!DNI.matcher(d).matches()) {
            throw new NegocioException("El DNI debe tener 8 dígitos.");
        }
        String t = limpiar(telefono);
        if (!TEL.matcher(t).matches()) {
            throw new NegocioException("El teléfono no es válido.");
        }
        String e = validarEmail(email);
        validarContrasena(password);
        return new Persona(n, a, d, t, e, password);
    }

    /** Normaliza (sin espacios, minúsculas) y valida el formato de un correo. Lo usan el registro y el cambio de credenciales. */
    public String validarEmail(String email) {
        String e = limpiar(email).toLowerCase();
        if (e.length() > 190 || !EMAIL.matcher(e).matches()) {
            throw new NegocioException("El correo electrónico no es válido.");
        }
        return e;
    }

    /** Reglas de contraseña del sistema (mínimo 8 caracteres; BCrypt admite 72 bytes). Una sola fuente de verdad. */
    public void validarContrasena(String password) {
        if (password == null || password.length() < 8) {
            throw new NegocioException("La contraseña debe tener al menos 8 caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new NegocioException("La contraseña es demasiado larga (máximo 72 caracteres).");
        }
    }

    private Usuario crearUsuario(Persona p, String rolNombre, EstadoUsuario estado) {
        Rol rol = roles.findByNombre(rolNombre)
                .orElseThrow(() -> new NegocioException("Configuración incompleta: falta el rol " + rolNombre + "."));
        Usuario u = new Usuario();
        u.setNombres(p.nombres());
        u.setApellidos(p.apellidos());
        u.setDni(p.dni());
        u.setEmail(p.email());
        u.setTelefono(p.telefono());
        u.setPasswordHash(encoder.encode(p.password()));
        u.setEstado(estado);
        u.setRol(rol);
        return usuarios.save(u);
    }

    private void crearSolicitud(Medico m, String sustento) {
        SolicitudVerificacion s = new SolicitudVerificacion();
        s.setMedico(m);
        s.setSustentoUrl(sustento);
        s.setEstado(EstadoSolicitud.PENDIENTE);
        solicitudes.save(s);
    }

    /** Solo http/https: el admin abre este enlace, así que se bloquean esquemas como javascript:. */
    private String validarUrl(String raw) {
        String url = limpiar(raw);
        if (url.isEmpty() || url.length() > 500) {
            throw new NegocioException("El enlace de sustento es obligatorio (máximo 500 caracteres).");
        }
        try {
            URI uri = new URI(url);
            String esquema = uri.getScheme();
            boolean ok = esquema != null
                    && (esquema.equalsIgnoreCase("http") || esquema.equalsIgnoreCase("https"))
                    && uri.getHost() != null;
            if (!ok) {
                throw new NegocioException("El enlace de sustento debe empezar con http:// o https://");
            }
        } catch (URISyntaxException ex) {
            throw new NegocioException("El enlace de sustento no es válido.");
        }
        return url;
    }

    private String exigir(String valor, String campo, int max) {
        String v = limpiar(valor);
        if (v.isEmpty()) {
            throw new NegocioException(campo + " es obligatorio.");
        }
        if (v.length() > max) {
            throw new NegocioException(campo + " no puede superar " + max + " caracteres.");
        }
        return v;
    }

    private String limpiar(String s) {
        return s == null ? "" : s.trim();
    }
}
