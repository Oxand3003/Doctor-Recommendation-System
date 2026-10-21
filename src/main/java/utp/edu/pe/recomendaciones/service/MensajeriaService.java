package utp.edu.pe.recomendaciones.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import utp.edu.pe.recomendaciones.dto.ChatViews.Contact;
import utp.edu.pe.recomendaciones.dto.ChatViews.Conversation;
import utp.edu.pe.recomendaciones.dto.ChatViews.LastMessage;
import utp.edu.pe.recomendaciones.dto.ChatViews.Message;
import utp.edu.pe.recomendaciones.dto.ChatViews.MessageEvent;
import utp.edu.pe.recomendaciones.dto.ChatViews.MessageStatus;
import utp.edu.pe.recomendaciones.dto.ChatViews.ReadReceipt;
import utp.edu.pe.recomendaciones.dto.ChatViews.SendMessageRequest;
import utp.edu.pe.recomendaciones.dto.ChatViews.TypingEvent;
import utp.edu.pe.recomendaciones.model.Conversacion;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.MensajeChat;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.EstadoCita;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.repository.CitaRepository;
import utp.edu.pe.recomendaciones.repository.ConversacionRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.repository.MensajeChatRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

/** Persistencia, autorización y estados de lectura del chat paciente-médico. */
@Service
public class MensajeriaService {

    private static final String SALUDO_NEUTRO =
            "Hola, gracias por solicitar una cita conmigo. Estoy aquí para ayudarte con consultas sobre tu cita.";

    private final ConversacionRepository conversaciones;
    private final MensajeChatRepository mensajes;
    private final CitaRepository citas;
    private final UsuarioRepository usuarios;
    private final MedicoRepository medicos;
    private final ChatRealtimePublisher realtime;

    public MensajeriaService(ConversacionRepository conversaciones, MensajeChatRepository mensajes,
                             CitaRepository citas, UsuarioRepository usuarios, MedicoRepository medicos,
                             ChatRealtimePublisher realtime) {
        this.conversaciones = conversaciones;
        this.mensajes = mensajes;
        this.citas = citas;
        this.usuarios = usuarios;
        this.medicos = medicos;
        this.realtime = realtime;
    }

    /**
     * Reserva: reutiliza la conversación única del par y persiste un saludo del médico por cada nueva cita.
     * Este método comparte la transacción de CitaPacienteService.
     */
    @Transactional
    public void iniciarPorReserva(Usuario paciente, Medico medico) {
        var encontrada = conversaciones.findByPaciente_IdAndMedico_Id(paciente.getId(), medico.getId());
        Conversacion conversacion = encontrada.orElseGet(() ->
                conversaciones.saveAndFlush(new Conversacion(paciente, medico)));

        String configurado = medico.getMensajePredeterminadoChat();
        String texto = configurado == null || configurado.isBlank() ? SALUDO_NEUTRO : configurado.trim();
        MensajeChat mensaje = mensajes.saveAndFlush(new MensajeChat(conversacion, medico.getUsuario(), texto, null));
        conversacion.marcarActividad();
        conversaciones.save(conversacion);

        // Los dos participantes reciben el saludo; la vista calcula mine y unread según cada sesión.
        realtime.mensajeTrasCommit(paciente.getEmail(), evento(conversacion, mensaje, paciente));
        realtime.mensajeTrasCommit(medico.getUsuario().getEmail(), evento(conversacion, mensaje, medico.getUsuario()));
    }

    /** Incluye el historial incluso si la cita terminó; canSend refleja el permiso actual. */
    @Transactional(readOnly = true)
    public List<Conversation> listar(String email) {
        Usuario actual = usuarioActivo(email);
        String rol = actual.getRol().getNombre();
        if ("PACIENTE".equals(rol)) {
            return conversaciones.findByPaciente_IdOrderByActualizadoEnDesc(actual.getId()).stream()
                    .map(c -> vista(c, actual, c.getMedico().getId(), contactoMedico(c.getMedico())))
                    .toList();
        }
        if ("MEDICO".equals(rol)) {
            Medico medico = medicoActual(actual);
            return conversaciones.findByMedico_IdOrderByActualizadoEnDesc(medico.getId()).stream()
                    .map(c -> vista(c, actual, medico.getId(), contactoPaciente(c.getPaciente())))
                    .toList();
        }
        throw prohibido("Solo pacientes y médicos pueden acceder a la mensajería.");
    }

    @Transactional(readOnly = true)
    public List<Message> listarMensajes(String email, Long conversacionId) {
        Usuario actual = usuarioActivo(email);
        Conversacion conversacion = conversacionDeParticipante(conversacionId, actual);
        return mensajes.findByConversacion_IdOrderByCreadoEnAsc(conversacion.getId()).stream()
                .map(m -> vista(m, actual)).toList();
    }

    @Transactional
    public Message enviar(String email, Long conversacionId, SendMessageRequest solicitud) {
        Usuario actual = usuarioActivo(email);
        Conversacion conversacion = conversacionDeParticipante(conversacionId, actual);
        comprobarCitaPendiente(conversacion);

        String texto = solicitud.text() == null ? "" : solicitud.text().trim();
        if (texto.isEmpty() || texto.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mensaje debe tener entre 1 y 1000 caracteres.");
        }
        String clientId = solicitud.clientId() == null || solicitud.clientId().isBlank()
                ? null : solicitud.clientId().trim();
        if (clientId != null) {
            var existente = mensajes.findByConversacion_IdAndClientId(conversacion.getId(), clientId);
            if (existente.isPresent()) {
                if (!existente.get().getEmisor().getId().equals(actual.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "El identificador del mensaje ya fue utilizado.");
                }
                return vista(existente.get(), actual);
            }
        }

        MensajeChat guardado = mensajes.saveAndFlush(new MensajeChat(conversacion, actual, texto, clientId));
        conversacion.marcarActividad();
        conversaciones.save(conversacion);
        Usuario destinatario = esPaciente(conversacion, actual)
                ? conversacion.getMedico().getUsuario() : conversacion.getPaciente();
        realtime.mensajeTrasCommit(destinatario.getEmail(), evento(conversacion, guardado, destinatario));
        return vista(guardado, actual);
    }

    /** Solo marca mensajes recibidos por el participante autenticado; nunca acepta el id del lector del cliente. */
    @Transactional
    public ReadReceipt marcarLeidos(String email, Long conversacionId) {
        Usuario lector = usuarioActivo(email);
        Conversacion conversacion = conversacionDeParticipante(conversacionId, lector);
        List<MensajeChat> pendientes = mensajes.pendientesDeLectura(conversacionId, lector.getId());
        LocalDateTime leidoEn = LocalDateTime.now();
        pendientes.forEach(m -> m.setLeidoEn(leidoEn));
        mensajes.saveAll(pendientes);

        for (MensajeChat mensaje : pendientes) {
            realtime.estadoTrasCommit(mensaje.getEmisor().getEmail(), new MessageStatus(
                    conversacionId, mensaje.getId(), "read", leidoEn));
        }
        return new ReadReceipt(conversacionId, lector.getId(), leidoEn,
                pendientes.stream().map(MensajeChat::getId).toList());
    }

    /** Comprueba conexión/destino STOMP antes de que el frame llegue al controlador. */
    @Transactional(readOnly = true)
    public void validarDestinoEnvio(String email, Long conversacionId) {
        Conversacion conversacion = conversacionDeParticipante(conversacionId, usuarioActivo(email));
        comprobarCitaPendiente(conversacion);
    }

    @Transactional(readOnly = true)
    public void validarDestinoLectura(String email, Long conversacionId) {
        conversacionDeParticipante(conversacionId, usuarioActivo(email));
    }

    /** Notifica solo al otro participante y mientras la cita siga pendiente. */
    @Transactional(readOnly = true)
    public void notificarEscribiendo(String email, Long conversacionId, boolean escribiendo) {
        Usuario actual = usuarioActivo(email);
        Conversacion conversacion = conversacionDeParticipante(conversacionId, actual);
        comprobarCitaPendiente(conversacion);
        Usuario destinatario = esPaciente(conversacion, actual)
                ? conversacion.getMedico().getUsuario() : conversacion.getPaciente();
        realtime.escribiendo(destinatario.getEmail(), new TypingEvent(conversacionId, escribiendo));
    }

    private MessageEvent evento(Conversacion conversacion, MensajeChat mensaje, Usuario destinatario) {
        boolean paciente = conversacion.getPaciente().getId().equals(destinatario.getId());
        Contact contacto = paciente ? contactoMedico(conversacion.getMedico())
                : contactoPaciente(conversacion.getPaciente());
        return new MessageEvent(vista(conversacion, destinatario, conversacion.getMedico().getId(), contacto),
                vista(mensaje, destinatario));
    }

    private Conversation vista(Conversacion conversacion, Usuario actual, Long medicoId, Contact contacto) {
        LastMessage ultimo = mensajes.findFirstByConversacion_IdOrderByCreadoEnDesc(conversacion.getId())
                .map(m -> {
                    boolean mio = m.getEmisor().getId().equals(actual.getId());
                    return new LastMessage(m.getId(), m.getTexto(), m.getCreadoEn(), mio,
                            mio && m.getLeidoEn() != null ? "read" : "sent");
                }).orElse(null);
        long unread = mensajes.contarNoLeidos(conversacion.getId(), actual.getId());
        boolean puedeEnviar = citas.existsByPaciente_IdAndMedico_IdAndEstado(
                conversacion.getPaciente().getId(), medicoId, EstadoCita.PENDIENTE);
        return new Conversation(conversacion.getId(), contacto, ultimo, unread, puedeEnviar);
    }

    private Message vista(MensajeChat mensaje, Usuario actual) {
        boolean mio = mensaje.getEmisor().getId().equals(actual.getId());
        return new Message(mensaje.getId(), mensaje.getConversacion().getId(), mio, mensaje.getTexto(),
                mensaje.getCreadoEn(), mensaje.getLeidoEn(), mio && mensaje.getLeidoEn() != null ? "read" : "sent",
                mensaje.getClientId());
    }

    private Contact contactoMedico(Medico medico) {
        return new Contact(medico.getId(), DoctorProfileService.nombrePublico(medico.getUsuario()),
                medico.getEspecialidad().getNombre());
    }

    private Contact contactoPaciente(Usuario paciente) {
        return new Contact(paciente.getId(), (paciente.getNombres() + " " + paciente.getApellidos()).trim(), "Paciente");
    }

    private Usuario usuarioActivo(String email) {
        Usuario usuario = usuarios.findByEmail(email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (usuario.getEstado() != EstadoUsuario.ACTIVO) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return usuario;
    }

    private Medico medicoActual(Usuario usuario) {
        return medicos.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }

    private Conversacion conversacionDeParticipante(Long id, Usuario usuario) {
        Conversacion conversacion = conversaciones.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!esPaciente(conversacion, usuario)
                && !conversacion.getMedico().getUsuario().getId().equals(usuario.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return conversacion;
    }

    private boolean esPaciente(Conversacion conversacion, Usuario usuario) {
        return conversacion.getPaciente().getId().equals(usuario.getId());
    }

    private void comprobarCitaPendiente(Conversacion conversacion) {
        if (!citas.existsByPaciente_IdAndMedico_IdAndEstado(conversacion.getPaciente().getId(),
                conversacion.getMedico().getId(), EstadoCita.PENDIENTE)) {
            throw prohibido("Solo se pueden enviar mensajes mientras exista una cita pendiente.");
        }
    }

    private ResponseStatusException prohibido(String mensaje) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, mensaje);
    }
}
