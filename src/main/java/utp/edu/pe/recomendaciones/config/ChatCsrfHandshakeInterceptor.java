package utp.edu.pe.recomendaciones.config;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.http.server.ServletServerHttpRequest;

/** Copia a la sesión WebSocket el token CSRF que ya entregó la página del chat. */
@Component
public class ChatCsrfHandshakeInterceptor implements HandshakeInterceptor {

    public static final String SESSION_TOKEN_ATTRIBUTE = "medicerca.chat.csrf";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) return false;
        HttpSession session = servletRequest.getServletRequest().getSession(false);
        if (session == null) return false;
        Object token = session.getAttribute(SESSION_TOKEN_ATTRIBUTE);
        if (!(token instanceof String csrfToken) || csrfToken.isBlank()) return false;
        attributes.put(SESSION_TOKEN_ATTRIBUTE, csrfToken);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No se conserva estado fuera de la sesión autenticada.
    }
}
