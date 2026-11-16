package utp.edu.pe.recomendaciones.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/**
 * El cliente de la IA contra un servidor local que imita la API chat/completions (sin salir a internet):
 * formato de la petición, modelo de respaldo cuando el principal falla y tolerancia a parámetros rechazados.
 */
class ClienteIaTest {

    private static final String OK = "{\"choices\":[{\"message\":{\"content\":\"{\\\"reply\\\":\\\"hola\\\"}\"}}]}";
    private static final String SATURADO = "[{\n  \"error\": {\n    \"code\": 503,\n    \"message\": \"The model is overloaded.\"\n  }\n}]";

    private record Falsa(int estado, String cuerpo) { }

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> pedidos = new CopyOnWriteArrayList<>();
    private HttpServer servidor;

    @AfterEach
    void apagar() {
        if (servidor != null) servidor.stop(0);
    }

    private ClienteIa clienteCon(String modelos, Function<JsonNode, Falsa> ia) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/chat/completions", exchange -> {
            JsonNode pedido = mapper.readTree(exchange.getRequestBody().readAllBytes());
            pedidos.add(pedido);
            Falsa respuesta = ia.apply(pedido);
            byte[] bytes = respuesta.cuerpo().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(respuesta.estado(), bytes.length);
            try (OutputStream salida = exchange.getResponseBody()) {
                salida.write(bytes);
            }
        });
        servidor.start();
        return new ClienteIa("clave-de-prueba", "http://127.0.0.1:" + servidor.getAddress().getPort() + "/",
                modelos, "low", 5);
    }

    private List<String> modelosPedidos() {
        return pedidos.stream().map(p -> p.path("model").asText()).toList();
    }

    @Test
    void enviaElFormatoEsperadoYDevuelveElJsonDeLaIa() throws IOException {
        ClienteIa ia = clienteCon("principal", pedido -> new Falsa(200, OK));

        JsonNode respuesta = ia.consultar("instrucciones", "me duele la cabeza");

        assertThat(respuesta.path("reply").asText()).isEqualTo("hola");
        JsonNode pedido = pedidos.get(0);
        assertThat(pedido.path("model").asText()).isEqualTo("principal");
        assertThat(pedido.path("reasoning_effort").asText()).isEqualTo("low");
        assertThat(pedido.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(pedido.has("temperature")).isFalse();
        assertThat(pedido.path("messages").path(0).path("content").asText()).isEqualTo("instrucciones");
        assertThat(pedido.path("messages").path(1).path("content").asText()).isEqualTo("me duele la cabeza");
    }

    @Test
    void siElModeloPrincipalEstaSaturadoRespondeElDeRespaldoYElPrincipalSePausa() throws IOException {
        ClienteIa ia = clienteCon("principal, respaldo", pedido ->
                "principal".equals(pedido.path("model").asText()) ? new Falsa(503, SATURADO) : new Falsa(200, OK));

        assertThat(ia.consultar("i", "m").path("reply").asText()).isEqualTo("hola");
        assertThat(ia.consultar("i", "m").path("reply").asText()).isEqualTo("hola");

        // La segunda consulta ya no hace esperar al paciente con el modelo que acaba de fallar.
        assertThat(modelosPedidos()).containsExactly("principal", "respaldo", "respaldo");
    }

    @Test
    void siNingunModeloRespondeDevuelveNull() throws IOException {
        ClienteIa ia = clienteCon("principal,respaldo", pedido -> new Falsa(503, SATURADO));

        assertThat(ia.consultar("i", "m")).isNull();
        assertThat(modelosPedidos()).containsExactly("principal", "respaldo");
    }

    @Test
    void siElModeloRechazaReasoningEffortSeReintentaSinEl() throws IOException {
        ClienteIa ia = clienteCon("principal", pedido -> pedido.has("reasoning_effort")
                ? new Falsa(400, "[{\"error\":{\"message\":\"Unsupported reasoning_effort\"}}]") : new Falsa(200, OK));

        assertThat(ia.consultar("i", "m").path("reply").asText()).isEqualTo("hola");
        assertThat(ia.consultar("i", "m").path("reply").asText()).isEqualTo("hola");

        // Primera consulta: con el parámetro (400) y sin él (200). La siguiente va directo sin el parámetro.
        assertThat(pedidos).hasSize(3);
        assertThat(pedidos.get(2).has("reasoning_effort")).isFalse();
    }

    @Test
    void sinClaveNoEstaDisponibleNiLlamaALaIa() throws IOException {
        clienteCon("principal", pedido -> new Falsa(200, OK));
        ClienteIa sinClave = new ClienteIa("", "http://127.0.0.1:" + servidor.getAddress().getPort(), "principal", "low", 5);

        assertThat(sinClave.disponible()).isFalse();
        assertThat(sinClave.consultar("i", "m")).isNull();
        assertThat(pedidos).isEmpty();
    }

    @Test
    void elErrorDeLaIaQuedaEnUnaSolaLinea() {
        assertThat(ClienteIa.unaLinea(SATURADO))
                .isEqualTo("[{ \"error\": { \"code\": 503, \"message\": \"The model is overloaded.\" } }]");
        assertThat(ClienteIa.unaLinea("x".repeat(500))).hasSize(401).endsWith("…");
        assertThat(ClienteIa.unaLinea(null)).isEmpty();
    }
}
