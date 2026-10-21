package utp.edu.pe.recomendaciones.Controller;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import utp.edu.pe.recomendaciones.dto.ChatConversationView;
import utp.edu.pe.recomendaciones.dto.ChatRecommendationRequest;
import utp.edu.pe.recomendaciones.dto.ChatRecommendationResponse;
import utp.edu.pe.recomendaciones.service.ChatRecommendationService;

@RestController
@RequestMapping("/api/chat")
public class ChatRecommendationController {
    private final ChatRecommendationService recommendations;

    public ChatRecommendationController(ChatRecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @PostMapping("/recommendations")
    public ChatRecommendationResponse recommend(@Valid @RequestBody ChatRecommendationRequest request,
                                                Authentication auth) {
        return recommendations.recommend(request, auth == null ? null : auth.getName());
    }

    /** Conversación guardada del paciente con el asistente (o una nueva con el saludo). */
    @GetMapping("/conversation")
    public ChatConversationView conversation(Authentication auth) {
        return recommendations.conversation(auth.getName());
    }

    /** "Nueva conversación": borra la anterior y devuelve una vacía con el saludo. */
    @DeleteMapping("/conversation")
    public ChatConversationView newConversation(Authentication auth) {
        return recommendations.newConversation(auth.getName());
    }
}
