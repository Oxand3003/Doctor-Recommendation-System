package utp.edu.pe.recomendaciones.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/** STOMP sobre WebSocket nativo; los mensajes de usuario solo se enrutan a las colas privadas. */
@Configuration
@EnableWebSocketMessageBroker
public class MensajeriaWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatStompAuthorizationInterceptor autorizacion;
    private final ChatCsrfHandshakeInterceptor csrfHandshake;

    public MensajeriaWebSocketConfig(ChatStompAuthorizationInterceptor autorizacion,
                                     ChatCsrfHandshakeInterceptor csrfHandshake) {
        this.autorizacion = autorizacion;
        this.csrfHandshake = csrfHandshake;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Sin orígenes externos permitidos: el handshake conserva la política same-origin predeterminada.
        registry.addEndpoint("/ws-mensajeria").addInterceptors(csrfHandshake);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableSimpleBroker("/queue");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(autorizacion);
    }
}
