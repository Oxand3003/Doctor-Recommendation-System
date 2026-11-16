package utp.edu.pe.recomendaciones.service;

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
import utp.edu.pe.recomendaciones.model.SustentoSolicitud;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.EstadoSolicitud;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;
import utp.edu.pe.recomendaciones.repository.EstablecimientoRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.repository.RolRepository;
import utp.edu.pe.recomendaciones.repository.SolicitudVerificacionRepository;
import utp.edu.pe.recomendaciones.repository.SustentoSolicitudRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

@Service
public class RegistroService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern DNI = Pattern.compile("^[0-9]{8}$");
    private static final Pattern TEL = Pattern.compile("^[0-9]{9}$");
    /** Nombres y apellidos: letras (con tildes), espacios, apóstrofo, guion y punto. */
    private static final Pattern NOMBRE = Pattern.compile("^\\p{L}[\\p{L} '.\\-]{1,99}$");
    /** El número de colegiatura del CMP es solo numérico (hasta 6 dígitos); el RNE también. */
    private static final Pattern CMP = Pattern.compile("^[0-9]{4,6}$");
    private static final Pattern RNE = Pattern.compile("^[0-9]{1,6}$");
    private static final Pattern ESPECIALIDAD = Pattern.compile("^\\p{L}[\\p{L} ]{3,59}$");
    private static final Pattern CON_LETRA = Pattern.compile(".*\\p{L}.*");
    private static final Pattern CON_DIGITO = Pattern.compile(".*\\d.*");

    /** Tamaño máximo del archivo de sustento (el límite de subida de Spring es 6 MB). */
    public static final int MAX_SUSTENTO = 5 * 1024 * 1024;

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final MedicoRepository medicos;
    private final EspecialidadRepository especialidades;
    private final EstablecimientoRepository establecimientos;
    private final SolicitudVerificacionRepository solicitudes;
    private final SustentoSolicitudRepository sustentos;
    private final PasswordEncoder encoder;

    public RegistroService(UsuarioRepository usuarios, RolRepository roles, MedicoRepository medicos,
                           EspecialidadRepository especialidades, EstablecimientoRepository establecimientos,
                           SolicitudVerificacionRepository solicitudes, SustentoSolicitudRepository sustentos,
                           PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.medicos = medicos;
        this.especialidades = especialidades;
        this.establecimientos = establecimientos;
        this.solicitudes = solicitudes;
        this.sustentos = sustentos;
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
        // La cuenta de un médico maneja datos de pacientes: además del mínimo general, letra y número.
        if (!CON_LETRA.matcher(f.password()).matches() || !CON_DIGITO.matcher(f.password()).matches()) {
            throw new NegocioException("La contraseña debe combinar letras y números.");
        }
        if (f.passwordConfirmacion() != null && !f.password().equals(f.passwordConfirmacion())) {
            throw new NegocioException("Las contraseñas no coinciden.");
        }

        String cmp = limpiar(f.cmp());
        if (!CMP.matcher(cmp).matches()) {
            throw new NegocioException("El número CMP debe tener entre 4 y 6 dígitos (solo números).");
        }
        String rne = limpiar(f.rne());
        if (!rne.isEmpty() && !RNE.matcher(rne).matches()) {
            throw new NegocioException("El número RNE solo puede tener números (hasta 6 dígitos).");
        }
        String titulo = texto(f.tituloProfesional(), "El título profesional", 150);
        String universidad = texto(f.universidad(), "La universidad", 180);

        int anioActual = Year.now().getValue();
        if (f.anioEgreso() == null || f.anioEgreso() < 1950 || f.anioEgreso() > anioActual) {
            throw new NegocioException("El año de egreso debe estar entre 1950 y " + anioActual + ".");
        }
        if (f.aniosExperiencia() == null || f.aniosExperiencia() < 0 || f.aniosExperiencia() > 80) {
            throw new NegocioException("Los años de experiencia deben estar entre 0 y 80.");
        }
        if (f.aniosExperiencia() > anioActual - f.anioEgreso()) {
            throw new NegocioException("Los años de experiencia no pueden ser más que los años desde tu egreso ("
                    + (anioActual - f.anioEgreso()) + ").");
        }

        Especialidad esp = especialidad(f.especialidad());
        Establecimiento est = establecimiento(f.establecimiento());
        String indicado = est == null ? establecimientoIndicado(f.establecimiento()) : null;
        Sustento sustento = validarSustento(f.sustento());

        // ¿Es un médico rechazado que vuelve a enviar su solicitud?
        Optional<Usuario> existente = usuarios.findByEmail(p.email());
        if (existente.isPresent()) {
            reenviar(existente.get(), p, cmp, rne, titulo, universidad, f, esp, est, indicado, sustento);
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

        crearSolicitud(m, indicado, sustento);
    }

    private void reenviar(Usuario u, Persona p, String cmp, String rne, String titulo, String universidad,
                          RegistroMedicoForm f, Especialidad esp, Establecimiento est, String indicado,
                          Sustento sustento) {
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

        crearSolicitud(m, indicado, sustento);
    }

    // ------------------------------------------------------------------
    // Especialidades activas (las que ven los pacientes)
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<OpcionView> especialidades() {
        return especialidades.findByActivaTrueOrderByNombreAsc().stream()
                .map(e -> new OpcionView(e.getId(), e.getNombre()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------
    private record Persona(String nombres, String apellidos, String dni, String telefono,
                           String email, String password) { }

    private Persona validarPersona(String nombres, String apellidos, String dni, String telefono,
                                   String email, String password) {
        String n = nombre(nombres, "Los nombres");
        String a = nombre(apellidos, "Los apellidos");
        String d = limpiar(dni);
        if (!DNI.matcher(d).matches()) {
            throw new NegocioException("El DNI debe tener 8 dígitos.");
        }
        String t = limpiar(telefono).replaceAll("[ +()\\-]", "");
        if (!TEL.matcher(t).matches() || t.charAt(0) != '9') {
            throw new NegocioException("El teléfono debe ser un celular de 9 dígitos que empiece con 9.");
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

    /**
     * La solicitud guarda en sustentoUrl la ruta del panel que sirve el archivo (las solicitudes antiguas tienen
     * ahí un enlace externo; vacío si no adjuntó constancia, que es opcional). La columna es NOT NULL y la ruta
     * necesita el id, por eso se guarda dos veces.
     */
    private void crearSolicitud(Medico m, String establecimientoIndicado, Sustento sustento) {
        SolicitudVerificacion s = new SolicitudVerificacion();
        s.setMedico(m);
        s.setSustentoUrl("");                      // sin constancia (es opcional): el panel lo indica
        s.setEstablecimientoIndicado(establecimientoIndicado);
        s.setEstado(EstadoSolicitud.PENDIENTE);
        s = solicitudes.save(s);
        if (sustento == null) return;
        s.setSustentoUrl(rutaSustento(s.getId()));
        solicitudes.save(s);

        SustentoSolicitud archivo = new SustentoSolicitud();
        archivo.setSolicitud(s);
        archivo.setNombre(sustento.nombre());
        archivo.setTipo(sustento.tipo());
        archivo.setTamano(sustento.datos().length);
        archivo.setDatos(sustento.datos());
        sustentos.save(archivo);
    }

    /** Ruta del panel de administración que muestra el archivo de sustento de una solicitud. */
    public static String rutaSustento(Long solicitudId) {
        return "/admin/api/applications/" + solicitudId + "/sustento";
    }

    /** Archivo ya validado: nombre seguro para mostrar y tipo detectado por su contenido. */
    private record Sustento(String nombre, String tipo, byte[] datos) { }

    /** La constancia es opcional (null si no adjuntó nada); si la adjunta, debe ser un archivo válido. */
    private Sustento validarSustento(RegistroMedicoForm.ArchivoSustento archivo) {
        if (archivo == null || archivo.datos() == null || archivo.datos().length == 0) {
            return null;
        }
        if (archivo.datos().length > MAX_SUSTENTO) {
            throw new NegocioException("El archivo de sustento supera el tamaño máximo de 5 MB.");
        }
        // Se decide por los bytes del archivo y no por la extensión ni el tipo que declara el navegador.
        String tipo = tipoSustento(archivo.datos());
        if (tipo == null) {
            throw new NegocioException("Formato no permitido. Adjunta un PDF o una imagen JPG, PNG o WEBP.");
        }
        String nombre = limpiar(archivo.nombre()).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (nombre.isEmpty()) nombre = "sustento";
        if (nombre.length() > 200) nombre = nombre.substring(nombre.length() - 200);
        return new Sustento(nombre, tipo, archivo.datos());
    }

    /** Content-Type según los primeros bytes, o null si no es PDF, JPG, PNG ni WEBP. */
    static String tipoSustento(byte[] b) {
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return "application/pdf";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    /**
     * Especialidad escrita por el médico. Si ya existe (sin importar mayúsculas ni tildes) se usa esa; si no, se
     * crea inactiva y el administrador la activa al aprobar la solicitud (ver AdminService.aprobar).
     */
    private Especialidad especialidad(String texto) {
        String nombre = limpiar(texto).replaceAll("\\s+", " ");
        if (nombre.isEmpty()) {
            throw new NegocioException("Escribe o elige tu especialidad.");
        }
        if (!ESPECIALIDAD.matcher(nombre).matches()) {
            throw new NegocioException("La especialidad solo puede tener letras y espacios (de 4 a 60 caracteres).");
        }
        String clave = AsistenteTexto.normalizar(nombre);
        return especialidades.findAll().stream()
                .filter(e -> AsistenteTexto.normalizar(e.getNombre()).equals(clave))
                .findFirst()
                .orElseGet(() -> {
                    Especialidad nueva = new Especialidad();
                    nueva.setNombre(Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1));
                    nueva.setDescripcion("Propuesta por un médico al registrarse.");
                    nueva.setActiva(false);
                    return especialidades.save(nueva);
                });
    }

    /**
     * Establecimiento del catálogo que coincide con lo escrito ("Nombre — Distrito" o solo el nombre, sin importar
     * tildes ni mayúsculas); null si no escribió nada o si no está en el catálogo (ver establecimientoIndicado).
     */
    private Establecimiento establecimiento(String texto) {
        String valor = AsistenteTexto.normalizar(texto);
        if (valor.isEmpty()) return null;
        List<Establecimiento> catalogo = establecimientos.findByPropietarioIsNull(Sort.by("nombre"));
        Optional<Establecimiento> exacto = catalogo.stream()
                .filter(e -> AsistenteTexto.normalizar(etiqueta(e)).equals(valor)).findFirst();
        if (exacto.isPresent()) return exacto.get();
        List<Establecimiento> porNombre = catalogo.stream()
                .filter(e -> AsistenteTexto.normalizar(e.getNombre()).equals(valor)).toList();
        return porNombre.size() == 1 ? porNombre.get(0) : null;
    }

    /** Establecimiento escrito que no está en el catálogo: se anota en la solicitud tal como lo escribió. */
    private String establecimientoIndicado(String texto) {
        String v = limpiar(texto).replaceAll("\\s+", " ");
        if (v.isEmpty()) return null;
        if (v.length() < 3 || v.length() > 150 || !CON_LETRA.matcher(v).matches()) {
            throw new NegocioException("El establecimiento debe tener entre 3 y 150 caracteres y contener letras.");
        }
        return v;
    }

    private static String etiqueta(Establecimiento e) {
        return e.getNombre() + " — " + e.getDistrito().getNombre();
    }

    private String nombre(String valor, String campo) {
        String v = exigir(valor, campo, 100).replaceAll("\\s+", " ");
        if (!NOMBRE.matcher(v).matches()) {
            throw new NegocioException(campo + " solo pueden tener letras y espacios (mínimo 2 letras).");
        }
        return v;
    }

    /** Texto obligatorio que debe contener letras (título profesional, universidad). */
    private String texto(String valor, String campo, int max) {
        String v = exigir(valor, campo, max);
        if (v.length() < 3 || !CON_LETRA.matcher(v).matches()) {
            throw new NegocioException(campo + " debe tener al menos 3 caracteres y contener letras.");
        }
        return v;
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
