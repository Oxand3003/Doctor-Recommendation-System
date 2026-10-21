package utp.edu.pe.recomendaciones.service;

import java.time.Year;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import utp.edu.pe.recomendaciones.dto.FormacionForm;
import utp.edu.pe.recomendaciones.dto.PerfilMedicoForm;
import utp.edu.pe.recomendaciones.dto.PerfilPrivadoView;
import utp.edu.pe.recomendaciones.dto.PerfilPrivadoView.FormacionItem;
import utp.edu.pe.recomendaciones.dto.PerfilPrivadoView.TipoOpcion;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Especialidad;
import utp.edu.pe.recomendaciones.model.Establecimiento;
import utp.edu.pe.recomendaciones.model.FormacionMedico;
import utp.edu.pe.recomendaciones.model.FotoMedico;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.model.enums.TipoFoto;
import utp.edu.pe.recomendaciones.model.enums.TipoFormacion;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;
import utp.edu.pe.recomendaciones.repository.EstablecimientoRepository;
import utp.edu.pe.recomendaciones.repository.FormacionMedicoRepository;
import utp.edu.pe.recomendaciones.repository.FotoMedicoRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

/**
 * Perfil privado del médico: edición de su información profesional pública, formación y fotografías.
 * El médico se identifica SIEMPRE por la sesión (correo); ningún método recibe un id de médico desde la
 * URL o el formulario, así que un médico no puede tocar el perfil de otro.
 */
@Service
public class PerfilMedicoService {

    public static final int MAX_GALERIA = 12;
    private static final int MAX_FORMACIONES = 30;
    private static final int MAX_BIOGRAFIA = 2000;
    private static final int MAX_MENSAJE_CHAT = 1000;

    private final UsuarioRepository usuarios;
    private final MedicoRepository medicos;
    private final EspecialidadRepository especialidades;
    private final EstablecimientoRepository establecimientos;
    private final FormacionMedicoRepository formaciones;
    private final FotoMedicoRepository fotos;
    private final FotoStorageService storage;
    private final RegistroService registro;
    private final SedeService sedesService;

    public PerfilMedicoService(UsuarioRepository usuarios, MedicoRepository medicos,
                               EspecialidadRepository especialidades, EstablecimientoRepository establecimientos,
                               FormacionMedicoRepository formaciones, FotoMedicoRepository fotos,
                               FotoStorageService storage, RegistroService registro,
                               SedeService sedesService) {
        this.usuarios = usuarios;
        this.medicos = medicos;
        this.especialidades = especialidades;
        this.establecimientos = establecimientos;
        this.formaciones = formaciones;
        this.fotos = fotos;
        this.storage = storage;
        this.registro = registro;
        this.sedesService = sedesService;
    }

    // ------------------------------------------------------------------
    // LECTURA
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public PerfilPrivadoView perfil(String email) {
        Medico m = medicoActual(email);
        Usuario u = m.getUsuario();

        String subtitulo = m.getEspecialidad().getNombre()
                + (m.getTituloProfesional() != null && !m.getTituloProfesional().isBlank()
                        ? " — " + m.getTituloProfesional() : "");

        PerfilMedicoForm datos = new PerfilMedicoForm(
                u.getNombres(), u.getApellidos(), m.getEspecialidad().getId(),
                m.getEstablecimiento() != null ? m.getEstablecimiento().getId() : null,
                m.getTituloProfesional(), m.getUniversidad(), m.getAnioEgreso(), m.getAniosExperiencia(),
                m.getBiografia(), m.getMensajePredeterminadoChat());

        List<FormacionItem> lista = formaciones.findByMedicoId(m.getId()).stream()
                .sorted(Comparator.comparing(FormacionMedico::getAnio,
                        Comparator.nullsLast(Comparator.<Integer>reverseOrder())))
                .map(f -> new FormacionItem(f.getId(), f.getTipo().name(), etiqueta(f.getTipo()),
                        f.getTitulo(), f.getInstitucion(), f.getAnio()))
                .toList();

        List<TipoOpcion> tipos = java.util.Arrays.stream(TipoFormacion.values())
                .map(t -> new TipoOpcion(t.name(), etiqueta(t))).toList();

        return new PerfilPrivadoView(
                m.getId(),
                DoctorProfileService.nombrePublico(u),
                DoctorProfileService.iniciales(u),
                m.getEspecialidad().getNombre(),
                subtitulo,
                u.getEmail(), u.getDni(), u.getTelefono(),
                m.getCmp(),
                m.getRne() == null || m.getRne().isBlank() ? "—" : m.getRne(),
                m.getEstablecimiento() != null ? m.getEstablecimiento().getNombre() : "Por confirmar",
                m.getCostoConsulta() == null ? "" : m.getCostoConsulta().stripTrailingZeros().toPlainString(),
                m.getTotalValoraciones() == 0 ? "Nuevo" : String.format(Locale.US, "%.1f", m.getRatingPromedio()),
                m.getTotalValoraciones(),
                fotos.findFirstByMedicoIdAndTipoOrderByCreadoEnDesc(m.getId(), TipoFoto.PERFIL)
                        .map(FotoMedico::getId).orElse(null),
                fotos.findByMedicoIdAndTipoOrderByCreadoEnDesc(m.getId(), TipoFoto.GALERIA).stream()
                        .map(FotoMedico::getId).toList(),
                MAX_GALERIA,
                datos,
                lista,
                registro.especialidades(),
                sedesService.opciones(m),
                tipos);
    }

    // ------------------------------------------------------------------
    // INFORMACIÓN PROFESIONAL
    // ------------------------------------------------------------------
    @Transactional
    public void actualizarPerfil(String email, PerfilMedicoForm f) {
        Medico m = medicoActual(email);
        Usuario u = m.getUsuario();

        String nombres = exigir(f.nombres(), "Los nombres", 100);
        String apellidos = exigir(f.apellidos(), "Los apellidos", 100);
        String titulo = exigir(f.tituloProfesional(), "El título profesional", 150);
        String universidad = exigir(f.universidad(), "La universidad", 180);

        int anioActual = Year.now().getValue();
        if (f.anioEgreso() == null || f.anioEgreso() < 1950 || f.anioEgreso() > anioActual) {
            throw new NegocioException("El año de egreso debe estar entre 1950 y " + anioActual + ".");
        }
        if (f.aniosExperiencia() == null || f.aniosExperiencia() < 0 || f.aniosExperiencia() > 80) {
            throw new NegocioException("Los años de experiencia deben estar entre 0 y 80.");
        }

        Especialidad esp = especialidadElegida(f.especialidadId(), m);
        Establecimiento est = null;
        if (f.establecimientoId() != null) {
            // Catálogo o sede propia; la sede de otro médico se trata como inexistente.
            est = establecimientos.findById(f.establecimientoId())
                    .filter(e -> sedesService.esElegible(e, m))
                    .orElseThrow(() -> new NegocioException("El establecimiento seleccionado no existe."));
        }

        String bio = limpiarTextoLargo(f.biografia());
        if (bio.length() > MAX_BIOGRAFIA) {
            throw new NegocioException("La descripción no puede superar " + MAX_BIOGRAFIA + " caracteres.");
        }

        String mensajeChat = limpiarTextoLargo(f.mensajePredeterminadoChat());
        if (mensajeChat.length() > MAX_MENSAJE_CHAT) {
            throw new NegocioException("El mensaje inicial del chat no puede superar " + MAX_MENSAJE_CHAT + " caracteres.");
        }

        u.setNombres(nombres);
        u.setApellidos(apellidos);
        usuarios.save(u);

        m.setEspecialidad(esp);
        m.setEstablecimiento(est);
        m.setTituloProfesional(titulo);
        m.setUniversidad(universidad);
        m.setAnioEgreso(f.anioEgreso());
        m.setAniosExperiencia(f.aniosExperiencia());
        m.setBiografia(bio.isEmpty() ? null : bio);
        m.setMensajePredeterminadoChat(mensajeChat.isEmpty() ? null : mensajeChat);
        medicos.save(m);
    }

    // ------------------------------------------------------------------
    // FORMACIÓN
    // ------------------------------------------------------------------
    @Transactional
    public void agregarFormacion(String email, FormacionForm f) {
        Medico m = medicoActual(email);
        if (formaciones.countByMedicoId(m.getId()) >= MAX_FORMACIONES) {
            throw new NegocioException("Alcanzaste el máximo de " + MAX_FORMACIONES + " registros de formación.");
        }
        FormacionMedico fm = new FormacionMedico();
        fm.setMedico(m);
        aplicar(fm, f);
        formaciones.save(fm);
    }

    @Transactional
    public void actualizarFormacion(String email, Long id, FormacionForm f) {
        Medico m = medicoActual(email);
        FormacionMedico fm = formaciones.findByIdAndMedicoId(id, m.getId())
                .orElseThrow(() -> new NegocioException("La formación indicada no existe."));
        aplicar(fm, f);
        formaciones.save(fm);
    }

    @Transactional
    public void eliminarFormacion(String email, Long id) {
        Medico m = medicoActual(email);
        FormacionMedico fm = formaciones.findByIdAndMedicoId(id, m.getId())
                .orElseThrow(() -> new NegocioException("La formación indicada no existe."));
        formaciones.delete(fm);
    }

    private void aplicar(FormacionMedico fm, FormacionForm f) {
        TipoFormacion tipo;
        try {
            tipo = TipoFormacion.valueOf(f.tipo() == null ? "" : f.tipo().trim());
        } catch (IllegalArgumentException e) {
            throw new NegocioException("Selecciona un tipo de formación válido.");
        }
        int anioActual = Year.now().getValue();
        if (f.anio() != null && (f.anio() < 1950 || f.anio() > anioActual)) {
            throw new NegocioException("El año debe estar entre 1950 y " + anioActual + ".");
        }
        fm.setTipo(tipo);
        fm.setTitulo(exigir(f.titulo(), "El título", 200));
        fm.setInstitucion(exigir(f.institucion(), "La institución", 200));
        fm.setAnio(f.anio());
    }

    // ------------------------------------------------------------------
    // FOTOS
    // ------------------------------------------------------------------
    /** Sube o reemplaza la foto de perfil (solo existe una). */
    @Transactional
    public void cambiarFotoPerfil(String email, MultipartFile archivo) {
        Medico m = medicoActual(email);
        FotoStorageService.Guardado g = storage.guardar(archivo);
        revertirArchivoSiFalla(g.archivo());

        List<FotoMedico> anteriores = fotos.findByMedicoIdAndTipoOrderByCreadoEnDesc(m.getId(), TipoFoto.PERFIL);
        fotos.deleteAll(anteriores);
        anteriores.forEach(a -> borrarArchivoTrasCommit(a.getArchivo()));
        fotos.save(nueva(m, TipoFoto.PERFIL, g));
    }

    @Transactional
    public void eliminarFotoPerfil(String email) {
        Medico m = medicoActual(email);
        List<FotoMedico> actuales = fotos.findByMedicoIdAndTipoOrderByCreadoEnDesc(m.getId(), TipoFoto.PERFIL);
        if (actuales.isEmpty()) {
            throw new NegocioException("No tienes una foto de perfil para eliminar.");
        }
        fotos.deleteAll(actuales);
        actuales.forEach(a -> borrarArchivoTrasCommit(a.getArchivo()));
    }

    /** Agrega varias fotos a la galería. Todo o nada: si una no es válida no se guarda ninguna. */
    @Transactional
    public int agregarFotosGaleria(String email, List<MultipartFile> archivos) {
        Medico m = medicoActual(email);
        List<MultipartFile> validos = archivos == null ? List.of()
                : archivos.stream().filter(a -> a != null && !a.isEmpty()).toList();
        if (validos.isEmpty()) {
            throw new NegocioException("Selecciona al menos una imagen.");
        }
        long actuales = fotos.countByMedicoIdAndTipo(m.getId(), TipoFoto.GALERIA);
        if (actuales + validos.size() > MAX_GALERIA) {
            throw new NegocioException("La galería admite hasta " + MAX_GALERIA + " fotos (tienes " + actuales + ").");
        }
        for (MultipartFile a : validos) {
            FotoStorageService.Guardado g = storage.guardar(a);
            revertirArchivoSiFalla(g.archivo());
            fotos.save(nueva(m, TipoFoto.GALERIA, g));
        }
        return validos.size();
    }

    @Transactional
    public void eliminarFotoGaleria(String email, Long fotoId) {
        Medico m = medicoActual(email);
        FotoMedico f = fotos.findByIdAndMedicoId(fotoId, m.getId())
                .filter(x -> x.getTipo() == TipoFoto.GALERIA)
                .orElseThrow(() -> new NegocioException("La foto indicada no existe."));
        fotos.delete(f);
        borrarArchivoTrasCommit(f.getArchivo());
    }

    /** Foto lista para servir. Solo las de médicos con cuenta ACTIVA son públicas. */
    public record FotoArchivo(Resource recurso, String tipoContenido) { }

    @Transactional(readOnly = true)
    public FotoArchivo foto(Long fotoId) {
        FotoMedico f = fotos.findById(fotoId)
                .filter(x -> x.getMedico().getUsuario().getEstado() == EstadoUsuario.ACTIVO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Resource r = storage.cargar(f.getArchivo());
        if (r == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return new FotoArchivo(r, f.getTipoContenido());
    }

    private FotoMedico nueva(Medico m, TipoFoto tipo, FotoStorageService.Guardado g) {
        FotoMedico f = new FotoMedico();
        f.setMedico(m);
        f.setTipo(tipo);
        f.setArchivo(g.archivo());
        f.setTipoContenido(g.tipoContenido());
        f.setTamanoBytes(g.tamano());
        return f;
    }

    /** Si la transacción no termina bien, el archivo recién escrito se borra del disco. */
    private void revertirArchivoSiFalla(String archivo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.eliminar(archivo);
            }
        });
    }

    /** El archivo solo se borra del disco cuando la base de datos ya confirmó el cambio. */
    private void borrarArchivoTrasCommit(String archivo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            storage.eliminar(archivo);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.eliminar(archivo);
            }
        });
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------
    private Medico medicoActual(String email) {
        Usuario u = usuarios.findByEmail(email == null ? "" : email.trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (!"MEDICO".equals(u.getRol().getNombre()) || u.getEstado() != EstadoUsuario.ACTIVO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return medicos.findByUsuarioId(u.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    /** La especialidad debe estar activa; si no cambia, se acepta aunque se haya desactivado después. */
    private Especialidad especialidadElegida(Long id, Medico m) {
        if (id == null) throw new NegocioException("Selecciona una especialidad.");
        if (id.equals(m.getEspecialidad().getId())) return m.getEspecialidad();
        Especialidad e = especialidades.findById(id)
                .orElseThrow(() -> new NegocioException("La especialidad seleccionada no existe."));
        if (!e.isActiva()) throw new NegocioException("La especialidad seleccionada no está disponible.");
        return e;
    }

    private String etiqueta(TipoFormacion t) {
        return switch (t) {
            case TITULO -> "Título profesional";
            case ESPECIALIDAD -> "Especialidad";
            case MEMBRESIA -> "Membresía";
            case PASANTIA -> "Pasantía / capacitación";
        };
    }

    /** Texto de una línea: sin caracteres de control, espacios repetidos colapsados. */
    private String exigir(String valor, String campo, int max) {
        String v = valor == null ? "" : valor.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s{2,}", " ");
        if (v.isEmpty()) throw new NegocioException(campo + " es obligatorio.");
        if (v.length() > max) throw new NegocioException(campo + " no puede superar " + max + " caracteres.");
        return v;
    }

    /** Texto multilínea: conserva los saltos de línea, quita otros caracteres de control. */
    private String limpiarTextoLargo(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").trim();
    }
}
