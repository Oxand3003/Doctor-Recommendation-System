package utp.edu.pe.recomendaciones.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import utp.edu.pe.recomendaciones.dto.ChatConversationView;
import utp.edu.pe.recomendaciones.dto.ChatConversationView.State;
import utp.edu.pe.recomendaciones.dto.ChatConversationView.Turn;
import utp.edu.pe.recomendaciones.model.AsistenteConversacion;
import utp.edu.pe.recomendaciones.repository.AsistenteConversacionRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

/**
 * Memoria del asistente de IA: guarda la conversación vigente de cada paciente. Al elegir "Nueva conversación"
 * se borra por completo y el asistente ya no la recuerda.
 */
@Service
public class AsistenteMemoriaService {
    private static final Logger log = LoggerFactory.getLogger(AsistenteMemoriaService.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    static final int MAX_TURNOS = 60;
    private static final int MAX_TEXTO = 1500;
    private static final TypeReference<List<Turn>> TURNOS = new TypeReference<>() { };

    private final AsistenteConversacionRepository conversaciones;
    private final UsuarioRepository usuarios;
    private final ObjectMapper mapper = new ObjectMapper();

    public AsistenteMemoriaService(AsistenteConversacionRepository conversaciones, UsuarioRepository usuarios) {
        this.conversaciones = conversaciones;
        this.usuarios = usuarios;
    }

    /** Conversación guardada del paciente, si existe. Una fecha ya pasada se descarta. */
    @Transactional(readOnly = true)
    public Optional<ChatConversationView> cargar(String email) {
        Long usuarioId = usuarioId(email);
        if (usuarioId == null) return Optional.empty();
        return conversaciones.findByUsuarioId(usuarioId).map(this::vista);
    }

    /** Guarda el estado y añade los mensajes nuevos. Un fallo aquí nunca debe romper la búsqueda. */
    @Transactional
    public void guardar(String email, State estado, List<Turn> nuevos) {
        try {
            Long usuarioId = usuarioId(email);
            if (usuarioId == null) return;
            AsistenteConversacion c = conversaciones.findByUsuarioId(usuarioId).orElseGet(() -> {
                AsistenteConversacion nueva = new AsistenteConversacion();
                nueva.setUsuarioId(usuarioId);
                return nueva;
            });
            List<Turn> turnos = new ArrayList<>(leer(c.getMensajes()));
            for (Turn t : nuevos) {
                if (t != null && t.content() != null && !t.content().isBlank()) {
                    turnos.add(new Turn(t.role(), recortar(t.content())));
                }
            }
            if (turnos.size() > MAX_TURNOS) turnos = new ArrayList<>(turnos.subList(turnos.size() - MAX_TURNOS, turnos.size()));
            c.setMensajes(mapper.writeValueAsString(turnos));
            c.setEspecialidad(estado.specialty());
            c.setDistrito(estado.district());
            c.setFecha(estado.date());
            c.setHora(estado.time());
            c.setOrden(estado.sort());
            c.setEtapa(estado.stage());
            c.setProposito(estado.purpose());
            conversaciones.save(c);
        } catch (Exception ex) {
            log.warn("No se pudo guardar la conversación del asistente: {}", ex.getMessage());
        }
    }

    /** "Nueva conversación": olvida todo lo anterior. */
    @Transactional
    public void olvidar(String email) {
        Long usuarioId = usuarioId(email);
        if (usuarioId != null) conversaciones.deleteByUsuarioId(usuarioId);
    }

    private Long usuarioId(String email) {
        if (email == null || email.isBlank()) return null;
        return usuarios.findByEmail(email).map(u -> u.getId()).orElse(null);
    }

    private ChatConversationView vista(AsistenteConversacion c) {
        LocalDate hoy = LocalDate.now(LIMA);
        boolean fechaVigente = c.getFecha() != null && !c.getFecha().isBefore(hoy);
        State estado = new State(c.getEspecialidad(), c.getDistrito(), fechaVigente ? c.getFecha() : null,
                fechaVigente ? c.getHora() : null, c.getOrden() == null ? "rating" : c.getOrden(),
                c.getEtapa() == null ? "MENU" : c.getEtapa(), c.getProposito());
        return new ChatConversationView(leer(c.getMensajes()), estado);
    }

    private List<Turn> leer(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, TURNOS);
        } catch (Exception ex) {
            log.warn("Conversación del asistente ilegible, se reinicia: {}", ex.getMessage());
            return List.of();
        }
    }

    private String recortar(String texto) {
        String t = texto.trim();
        return t.length() > MAX_TEXTO ? t.substring(0, MAX_TEXTO) : t;
    }
}
