package utp.edu.pe.recomendaciones.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Cliente de la IA externa (cualquier API compatible con el formato chat/completions), compartido por el
 * asistente de recomendaciones y la orientación corporal.
 * <p>
 * {@code medicerca.ai.model} admite varios modelos separados por coma: si el primero no responde (saturado,
 * tiempo de espera, error 4xx/5xx) se intenta el siguiente, y el que falló se deja de usar unos minutos para
 * no hacer esperar a cada paciente. Si ninguno responde se devuelve null y quien llama sigue con sus reglas.
 */
@Component
public class ClienteIa {
    private static final Logger log = LoggerFactory.getLogger(ClienteIa.class);
    private static final Duration PAUSA_TRAS_FALLO = Duration.ofMinutes(2);

    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient http;
    private final String apiKey;
    private final String endpoint;
    private final List<String> modelos;
    private final String reasoningEffort;
    private final Map<String, Instant> enPausa = new ConcurrentHashMap<>();
    /** Modelos que rechazaron el parámetro reasoning_effort: a esos ya no se les envía. */
    private final Set<String> sinRazonamiento = ConcurrentHashMap.newKeySet();

    private record Respuesta(int estado, String cuerpo) { }

    public ClienteIa(@Value("${medicerca.ai.api-key:}") String apiKey,
                     @Value("${medicerca.ai.base-url:https://generativelanguage.googleapis.com/v1beta/openai/}") String baseUrl,
                     @Value("${medicerca.ai.model:gemini-3.5-flash-lite,gemini-3.8-flash}") String modelos,
                     @Value("${medicerca.ai.reasoning-effort:low}") String reasoningEffort,
                     @Value("${medicerca.ai.timeout-seconds:12}") int timeoutSeconds) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.endpoint = (baseUrl == null ? "" : baseUrl.trim()).replaceAll("/+$", "") + "/chat/completions";
        this.modelos = Stream.of((modelos == null ? "" : modelos).split(","))
                .map(String::trim).filter(m -> !m.isEmpty()).distinct().toList();
        this.reasoningEffort = reasoningEffort == null ? "" : reasoningEffort.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)));
        this.http = RestClient.builder().requestFactory(factory).build();
        if (!disponible()) {
            log.warn("Asistente IA: no hay medicerca.ai.api-key; se usarán solo las reglas internas.");
        }
    }

    public boolean disponible() {
        return !apiKey.isEmpty() && !modelos.isEmpty();
    }

    /** Un solo llamado: el JSON que redactó la IA, o null si no hay clave o ningún modelo respondió. */
    public JsonNode consultar(String instrucciones, String mensaje) {
        if (!disponible()) return null;
        for (String modelo : modelos) {
            Instant hasta = enPausa.get(modelo);
            if (hasta != null && Instant.now().isBefore(hasta)) continue;
            long inicio = System.currentTimeMillis();
            try {
                JsonNode respuesta = llamar(modelo, instrucciones, mensaje);
                enPausa.remove(modelo);
                if (respuesta != null) return respuesta;
            } catch (RestClientException ex) {
                enPausa.put(modelo, Instant.now().plus(PAUSA_TRAS_FALLO));
                log.warn("Asistente IA: el modelo {} no respondió tras {} ms y se pausa {} min ({}): {}", modelo,
                        System.currentTimeMillis() - inicio, PAUSA_TRAS_FALLO.toMinutes(),
                        ex.getClass().getSimpleName(), ex.getMessage());
            } catch (Exception ex) {
                log.warn("Asistente IA: respuesta no válida del modelo {} ({}): {}", modelo,
                        ex.getClass().getSimpleName(), ex.getMessage());
            }
        }
        return null;
    }

    private JsonNode llamar(String modelo, String instrucciones, String mensaje) throws Exception {
        boolean conRazonamiento = !reasoningEffort.isEmpty() && !sinRazonamiento.contains(modelo);
        Respuesta r = enviar(modelo, instrucciones, mensaje, conRazonamiento);
        if (r.estado() == 400 && conRazonamiento) {
            // No todos los modelos aceptan reasoning_effort (o ese nivel): se reintenta sin el parámetro y,
            // si así responde, ya no se le vuelve a enviar a ese modelo.
            Respuesta sin = enviar(modelo, instrucciones, mensaje, false);
            if (sin.estado() < 400) {
                sinRazonamiento.add(modelo);
                log.warn("Asistente IA: el modelo {} no acepta reasoning_effort={} y se usará sin él: {}", modelo,
                        reasoningEffort, unaLinea(r.cuerpo()));
                r = sin;
            }
        }
        if (r.estado() >= 400) {
            throw new RestClientException("IA respondió " + r.estado() + ": " + unaLinea(r.cuerpo()));
        }
        if (r.cuerpo().isBlank()) return null;
        String content = mapper.readTree(r.cuerpo()).path("choices").path(0).path("message").path("content").asText("");
        content = content.replaceAll("(?s)^\\s*```(?:json)?\\s*|\\s*```\\s*$", "").trim();
        return content.isEmpty() ? null : mapper.readTree(content);
    }

    private Respuesta enviar(String modelo, String instrucciones, String mensaje, boolean conRazonamiento) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelo);
        body.put("messages", List.of(Map.of("role", "system", "content", instrucciones),
                Map.of("role", "user", "content", mensaje)));
        body.put("response_format", Map.of("type", "json_object"));
        // No se envía "temperature": los modelos Gemini actuales la ignoran y los siguientes la rechazarán.
        // Los modelos que "piensan" antes de responder tardan demasiado con su nivel de razonamiento por
        // defecto; para conversar y orientar hacia una especialidad basta un razonamiento bajo.
        if (conRazonamiento) body.put("reasoning_effort", reasoningEffort);
        // Se lee el cuerpo crudo con exchange() en vez de retrieve().body(String.class): así un error
        // (tiempo de espera, 4xx/5xx) llega al log con su causa real y con el cuerpo que devolvió la IA.
        return http.post().uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(mapper.writeValueAsString(body))
                .exchange((req, res) -> new Respuesta(res.getStatusCode().value(),
                        new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    /** El cuerpo de error en una sola línea y recortado, para que el log no lo parta ni lo trunque. */
    static String unaLinea(String texto) {
        String t = texto == null ? "" : texto.replaceAll("\\s+", " ").trim();
        return t.length() > 400 ? t.substring(0, 400) + "…" : t;
    }
}
