package utp.edu.pe.recomendaciones.Controller;

import java.security.Principal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;
import utp.edu.pe.recomendaciones.dto.ChatViews.ErrorNotice;
import utp.edu.pe.recomendaciones.dto.ChatViews.SendMessageRequest;
import utp.edu.pe.recomendaciones.dto.ChatViews.TypingRequest;
import utp.edu.pe.recomendaciones.service.ChatRealtimePublisher;
import utp.edu.pe.recomendaciones.service.MensajeriaService;

/** Destinos STOMP cerrados: solo enviar y marcar lectura en una conversación existente. */
@Controller
public class MensajeriaWebSocketController {

    private final MensajeriaService mensajeria;
    private final SimpMessagingTemplate messaging;

    public MensajeriaWebSocketController(MensajeriaService mensajeria, SimpMessagingTemplate messaging) {
        this.mensajeria = mensajeria;
        this.messaging = messaging;
    }

    @MessageMapping("/mensajeria/conversaciones/{id}/mensajes")
    public void enviar(@DestinationVariable Long id, SendMessageRequest solicitud, Principal principal) {
        var guardado = mensajeria.enviar(principal.getName(), id, solicitud);
        messaging.convertAndSendToUser(principal.getName(), ChatRealtimePublisher.ACK, guardado);
    }

    @MessageMapping("/mensajeria/conversaciones/{id}/leidos")
    public void marcarLeidos(@DestinationVariable Long id, Principal principal) {
        mensajeria.marcarLeidos(principal.getName(), id);
    }

    @MessageMapping("/mensajeria/conversaciones/{id}/escribiendo")
    public void escribiendo(@DestinationVariable Long id, TypingRequest solicitud, Principal principal) {
        mensajeria.notificarEscribiendo(principal.getName(), id, solicitud.typing());
    }

    @MessageExceptionHandler(ResponseStatusException.class)
    @SendToUser(destinations = ChatRealtimePublisher.ERRORS, broadcast = false)
    public ErrorNotice errorMensajeria(ResponseStatusException ex) {
        return new ErrorNotice(ex.getReason() == null ? "No se pudo completar la operación del chat." : ex.getReason());
    }
}
