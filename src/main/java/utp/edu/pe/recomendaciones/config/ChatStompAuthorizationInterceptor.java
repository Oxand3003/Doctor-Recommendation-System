package utp.edu.pe.recomendaciones.config;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import utp.edu.pe.recomendaciones.service.ChatRealtimePublisher;
import utp.edu.pe.recomendaciones.service.MensajeriaService;

/** Rechaza conexiones anónimas, subscriptions abiertas y SEND fuera de los destinos autorizados. */
@Component
public class ChatStompAuthorizationInterceptor implements ChannelInterceptor {

    private static final Pattern DESTINO = Pattern.compile(
            "^/app/mensajeria/conversaciones/([1-9][0-9]*)/(mensajes|leidos|escribiendo)$");

    private final MensajeriaService mensajeria;

    public ChatStompAuthorizationInterceptor(MensajeriaService mensajeria) {
        this.mensajeria = mensajeria;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) throw new MessageDeliveryException(message, "No se pudo validar el frame STOMP.");

        StompCommand command = headers.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            validarConexion(headers);
        } else if (command == StompCommand.SEND) {
            validarEnvio(headers);
        } else if (command == StompCommand.SUBSCRIBE) {
            validarSuscripcion(headers.getDestination());
        } else if (command == StompCommand.DISCONNECT || command == StompCommand.UNSUBSCRIBE
                || headers.getMessageType() == SimpMessageType.HEARTBEAT) {
            return message;
        } else {
            throw new MessageDeliveryException(message, "Comando STOMP no permitido.");
        }
        return message;
    }

    private void validarConexion(StompHeaderAccessor headers) {
        Principal principal = headers.getUser();
        if (!(principal instanceof Authentication auth) || !auth.isAuthenticated() || !tieneRolChat(auth)) {
            throw new MessageDeliveryException("Se requiere una sesión activa de paciente o médico.");
        }
        Object esperado = headers.getSessionAttributes() == null ? null
                : headers.getSessionAttributes().get(ChatCsrfHandshakeInterceptor.SESSION_TOKEN_ATTRIBUTE);
        String recibido = headers.getFirstNativeHeader("X-CSRF-TOKEN");
        if (!(esperado instanceof String token) || recibido == null || !token.equals(recibido)) {
            throw new MessageDeliveryException("Token CSRF inválido para la conexión de mensajería.");
        }
    }

    private void validarEnvio(StompHeaderAccessor headers) {
        Principal principal = headers.getUser();
        Matcher matcher = DESTINO.matcher(headers.getDestination() == null ? "" : headers.getDestination());
        if (!(principal instanceof Authentication auth) || !tieneRolChat(auth) || !matcher.matches()) {
            throw new MessageDeliveryException("Destino de mensajería no autorizado.");
        }
        long id;
        try {
            id = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            throw new MessageDeliveryException("Conversación no válida.");
        }
        if ("mensajes".equals(matcher.group(2))) {
            mensajeria.validarDestinoEnvio(auth.getName(), id);
        } else if ("leidos".equals(matcher.group(2))) {
            mensajeria.validarDestinoLectura(auth.getName(), id);
        } else {
            mensajeria.validarDestinoEnvio(auth.getName(), id);
        }
    }

    private void validarSuscripcion(String destino) {
        if (("/user" + ChatRealtimePublisher.INBOX).equals(destino)
                || ("/user" + ChatRealtimePublisher.ACK).equals(destino)
                || ("/user" + ChatRealtimePublisher.ERRORS).equals(destino)) return;
        throw new MessageDeliveryException("Suscripción de mensajería no autorizada.");
    }

    private boolean tieneRolChat(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(role -> "ROLE_PACIENTE".equals(role) || "ROLE_MEDICO".equals(role));
    }
}
