package utp.edu.pe.recomendaciones.Controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import utp.edu.pe.recomendaciones.dto.ChatViews.Conversation;
import utp.edu.pe.recomendaciones.dto.ChatViews.Message;
import utp.edu.pe.recomendaciones.dto.ChatViews.ReadReceipt;
import utp.edu.pe.recomendaciones.dto.ChatViews.SendMessageRequest;
import utp.edu.pe.recomendaciones.service.MensajeriaService;

/** API de persistencia del chat. No expone rutas para crear destinatarios ni eliminar historial. */
@RestController
@RequestMapping("/api/mensajeria")
public class MensajeriaController {

    private final MensajeriaService mensajeria;

    public MensajeriaController(MensajeriaService mensajeria) {
        this.mensajeria = mensajeria;
    }

    @GetMapping("/conversaciones")
    public List<Conversation> conversaciones(Authentication auth) {
        return mensajeria.listar(auth.getName());
    }

    @GetMapping("/conversaciones/{id}/mensajes")
    public List<Message> mensajes(Authentication auth, @PathVariable Long id) {
        return mensajeria.listarMensajes(auth.getName(), id);
    }

    @PostMapping("/conversaciones/{id}/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    public Message enviar(Authentication auth, @PathVariable Long id,
                          @Valid @RequestBody SendMessageRequest solicitud) {
        return mensajeria.enviar(auth.getName(), id, solicitud);
    }

    @PostMapping("/conversaciones/{id}/leidos")
    public ReadReceipt marcarLeidos(Authentication auth, @PathVariable Long id) {
        return mensajeria.marcarLeidos(auth.getName(), id);
    }
}
