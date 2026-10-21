package utp.edu.pe.recomendaciones.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.dto.ConsultaForm;
import utp.edu.pe.recomendaciones.dto.ConsultaView;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Consulta;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.CanalAtencion;
import utp.edu.pe.recomendaciones.model.enums.EstadoConsulta;
import utp.edu.pe.recomendaciones.model.enums.MotivoConsulta;
import utp.edu.pe.recomendaciones.repository.ConsultaRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

@Service
public class ConsultaService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TEL = Pattern.compile("^[0-9 +()\\-]{6,30}$");
    private static final int MAX_POR_HORA = 5;
    private static final int MENSAJE_MIN = 10;
    private static final int MENSAJE_MAX = 2000;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int RESPUESTA_MAX = 3000;

    private final ConsultaRepository consultas;
    private final UsuarioRepository usuarios;
    private final CorreoService correo;

    public ConsultaService(ConsultaRepository consultas, UsuarioRepository usuarios, CorreoService correo) {
        this.consultas = consultas;
        this.usuarios = usuarios;
        this.correo = correo;
    }

    /**
     * Valida y guarda la consulta. {@code emailSesion} es el correo de la cuenta con sesión
     * iniciada (o null si el visitante es anónimo) y solo sirve para vincular la consulta.
     */
    @Transactional
    public Consulta registrar(ConsultaForm f, String emailSesion) {
        String nombres = limpiar(f.nombres());
        String correo = limpiar(f.correo()).toLowerCase();
        String telefono = limpiar(f.telefono());
        String mensaje = f.mensaje() == null ? "" : f.mensaje().strip();

        if (nombres.length() < 3 || nombres.length() > 120) {
            throw new NegocioException("Escribe tus nombres (entre 3 y 120 caracteres).");
        }
        if (correo.length() > 150 || !EMAIL.matcher(correo).matches()) {
            throw new NegocioException("Escribe un correo electrónico válido.");
        }
        if (!TEL.matcher(telefono).matches() || telefono.replaceAll("\\D", "").length() < 6) {
            throw new NegocioException("Escribe un número de teléfono válido.");
        }
        MotivoConsulta motivo = motivo(f.motivo());
        if (mensaje.length() < MENSAJE_MIN) {
            throw new NegocioException("Cuéntanos con más detalle tu consulta (mínimo " + MENSAJE_MIN + " caracteres).");
        }
        if (mensaje.length() > MENSAJE_MAX) {
            throw new NegocioException("El mensaje no puede superar los " + MENSAJE_MAX + " caracteres.");
        }
        if (consultas.countByCorreoIgnoreCaseAndFechaCreacionAfter(correo, LocalDateTime.now().minusHours(1)) >= MAX_POR_HORA) {
            throw new NegocioException("Ya enviaste varias consultas en la última hora. Intenta de nuevo más tarde.");
        }

        Consulta c = new Consulta();
        c.setNombres(nombres);
        c.setCorreo(correo);
        c.setTelefono(telefono);
        c.setMotivo(motivo);
        c.setMensaje(mensaje);
        if (emailSesion != null && !emailSesion.isBlank()) {
            Usuario u = usuarios.findByEmail(emailSesion).orElse(null);
            c.setUsuario(u);
        }
        return consultas.save(c);
    }

    // ------------------------------------------------------------------
    // PANEL DE ADMINISTRACIÓN
    // ------------------------------------------------------------------

    /** Las 500 consultas más recientes (pendientes e historial); el panel las separa por estado. */
    @Transactional(readOnly = true)
    public List<ConsultaView> listar() {
        return consultas.findTop500ByOrderByFechaCreacionDesc().stream().map(this::toView).toList();
    }

    /**
     * Envía por correo el mensaje del administrador al usuario. Si el envío falla no se cambia nada
     * (NegocioException). Con {@code archivar} la consulta pasa de inmediato al historial.
     */
    public ConsultaView responderPorCorreo(Long id, String asunto, String mensaje, boolean archivar, String adminEmail) {
        Consulta c = buscar(id);
        String a = limpiar(asunto);
        String m = mensaje == null ? "" : mensaje.strip();
        if (a.isEmpty() || a.length() > 150) {
            throw new NegocioException("Escribe un asunto (máximo 150 caracteres).");
        }
        if (m.length() < 5) {
            throw new NegocioException("Escribe el mensaje que quieres enviar.");
        }
        if (m.length() > RESPUESTA_MAX) {
            throw new NegocioException("El mensaje no puede superar los " + RESPUESTA_MAX + " caracteres.");
        }

        String cuerpo = "Hola " + primerNombre(c.getNombres()) + ",\n\n" + m
                + "\n\n—\nEquipo MediCerca\n\n"
                + "----------------------------------------\n"
                + "Tu consulta (" + c.getMotivo().getEtiqueta() + ", " + c.getFechaCreacion().format(FECHA) + "):\n"
                + c.getMensaje() + "\n";
        correo.enviarTexto(c.getCorreo(), a, cuerpo);   // lanza NegocioException si no se pudo enviar

        c.setRespuesta(m);
        if (archivar && c.getEstado() != EstadoConsulta.ATENDIDA) {
            cerrar(c, CanalAtencion.CORREO, adminEmail);
        }
        return toView(consultas.save(c));
    }

    /** Pasa la consulta al historial indicando por qué medio se atendió. */
    @Transactional
    public ConsultaView marcarAtendida(Long id, String canal, String adminEmail) {
        Consulta c = buscar(id);
        if (c.getEstado() == EstadoConsulta.ATENDIDA) {
            throw new NegocioException("Esta consulta ya fue atendida.");
        }
        CanalAtencion ca;
        try {
            ca = CanalAtencion.valueOf(limpiar(canal));
        } catch (IllegalArgumentException e) {
            ca = CanalAtencion.OTRO;
        }
        cerrar(c, ca, adminEmail);
        return toView(consultas.save(c));
    }

    /** Devuelve una consulta del historial a pendientes. */
    @Transactional
    public ConsultaView reabrir(Long id) {
        Consulta c = buscar(id);
        if (c.getEstado() != EstadoConsulta.ATENDIDA) {
            throw new NegocioException("Esta consulta ya está pendiente.");
        }
        c.setEstado(EstadoConsulta.PENDIENTE);
        c.setFechaAtencion(null);
        c.setCanalAtencion(null);
        c.setAtendidaPor(null);
        return toView(consultas.save(c));
    }

    private void cerrar(Consulta c, CanalAtencion canal, String adminEmail) {
        c.setEstado(EstadoConsulta.ATENDIDA);
        c.setFechaAtencion(LocalDateTime.now());
        c.setCanalAtencion(canal);
        c.setAtendidaPor(adminEmail);
    }

    private Consulta buscar(Long id) {
        return consultas.findById(id).orElseThrow(() -> new NegocioException("La consulta no existe."));
    }

    private ConsultaView toView(Consulta c) {
        String numero = AgendaMedicoService.numeroWhatsapp(c.getTelefono());
        String wa = null;
        if (numero != null) {
            String texto = "Hola " + primerNombre(c.getNombres()) + ", te escribimos de MediCerca por tu consulta ("
                    + c.getMotivo().getEtiqueta().toLowerCase() + "). ";
            wa = "https://wa.me/" + numero + "?text=" + URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
        }
        CanalAtencion canal = c.getCanalAtencion();
        return new ConsultaView(c.getId(), c.getNombres(), c.getCorreo(), c.getTelefono(), wa,
                c.getMotivo().getEtiqueta(), c.getMotivo().name(), c.getMensaje(),
                c.getFechaCreacion().format(FECHA), c.getEstado().name(),
                c.getUsuario() != null,
                c.getFechaAtencion() == null ? null : c.getFechaAtencion().format(FECHA),
                canal == null ? null : canal.getEtiqueta(), c.getAtendidaPor(), c.getRespuesta());
    }

    private static String primerNombre(String nombres) {
        String n = nombres == null ? "" : nombres.strip();
        int i = n.indexOf(' ');
        return i < 0 ? n : n.substring(0, i);
    }

    private MotivoConsulta motivo(String valor) {
        try {
            return MotivoConsulta.valueOf(limpiar(valor));
        } catch (IllegalArgumentException e) {
            throw new NegocioException("Selecciona el motivo de tu consulta.");
        }
    }

    private String limpiar(String s) {
        return s == null ? "" : s.strip().replaceAll("\\s+", " ");
    }
}
