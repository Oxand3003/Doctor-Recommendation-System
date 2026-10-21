package utp.edu.pe.recomendaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/** Contratos REST y STOMP de la mensajería paciente-médico persistente. */
public final class ChatViews {
    private ChatViews() { }

    public record Contact(Long id, String name, String subtitle) { }

    public record LastMessage(Long id, String text, LocalDateTime createdAt, boolean mine, String status) { }

    public record Conversation(Long id, Contact contact, LastMessage lastMessage, long unread, boolean canSend) { }

    public record Message(Long id, Long conversationId, boolean mine, String text,
                          LocalDateTime createdAt, LocalDateTime readAt, String status, String clientId) { }

    public record MessageEvent(Conversation conversation, Message message) { }

    public record MessageStatus(Long conversationId, Long messageId, String status, LocalDateTime readAt) { }

    public record TypingEvent(Long conversationId, boolean typing) { }

    public record ReadReceipt(Long conversationId, Long readerId, LocalDateTime readAt, java.util.List<Long> messageIds) { }

    public record ErrorNotice(String message) { }

    public record SendMessageRequest(
            @NotBlank @Size(max = 1000) String text,
            @Size(max = 100) String clientId) { }

    public record TypingRequest(boolean typing) { }
}
