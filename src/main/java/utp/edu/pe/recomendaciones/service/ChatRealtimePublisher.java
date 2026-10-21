package utp.edu.pe.recomendaciones.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import utp.edu.pe.recomendaciones.dto.ChatViews.MessageEvent;
import utp.edu.pe.recomendaciones.dto.ChatViews.MessageStatus;
import utp.edu.pe.recomendaciones.dto.ChatViews.TypingEvent;

/** Publica eventos privados solo después de confirmar la transacción que guardó el mensaje/lectura. */
@Component
public class ChatRealtimePublisher {

    public static final String INBOX = "/queue/mensajeria";
    public static final String ACK = "/queue/mensajeria/ack";
    public static final String ERRORS = "/queue/mensajeria/errors";

    private final SimpMessagingTemplate messaging;

    public ChatRealtimePublisher(@Lazy SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void mensajeTrasCommit(String email, MessageEvent evento) {
        afterCommit(() -> messaging.convertAndSendToUser(email, INBOX, evento));
    }

    public void estadoTrasCommit(String email, MessageStatus estado) {
        afterCommit(() -> messaging.convertAndSendToUser(email, INBOX, estado));
    }

    public void escribiendo(String email, TypingEvent evento) {
        messaging.convertAndSendToUser(email, INBOX, evento);
    }

    private void afterCommit(Runnable publish) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish.run();
            }
        });
    }
}
