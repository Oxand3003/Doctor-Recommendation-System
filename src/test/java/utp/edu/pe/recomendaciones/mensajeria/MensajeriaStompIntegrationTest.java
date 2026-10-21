package utp.edu.pe.recomendaciones.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import utp.edu.pe.recomendaciones.model.Cita;
import utp.edu.pe.recomendaciones.model.Especialidad;
import utp.edu.pe.recomendaciones.model.HorarioAtencion;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.Rol;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.EstadoCita;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.model.enums.Modalidad;
import utp.edu.pe.recomendaciones.model.enums.ModalidadAtencion;
import utp.edu.pe.recomendaciones.repository.CitaRepository;
import utp.edu.pe.recomendaciones.repository.ConversacionRepository;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;
import utp.edu.pe.recomendaciones.repository.HorarioAtencionRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.repository.MensajeChatRepository;
import utp.edu.pe.recomendaciones.repository.RolRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

/** Verifica sesiones reales, persistencia, entrega STOMP privada y estados leídos en H2. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"medicerca.demo-data=false"})
class MensajeriaStompIntegrationTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final Pattern INPUT = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]*id=\"chat-app\"[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)=\"([^\"]*)\"");

    @LocalServerPort int port;
    @Autowired RolRepository roles;
    @Autowired UsuarioRepository usuarios;
    @Autowired MedicoRepository medicos;
    @Autowired EspecialidadRepository especialidades;
    @Autowired HorarioAtencionRepository horarios;
    @Autowired CitaRepository citas;
    @Autowired ConversacionRepository conversaciones;
    @Autowired MensajeChatRepository mensajes;
    @Autowired PasswordEncoder encoder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dosSesionesRealesConversanPorStompYElHistorialPermaneceEnLaBase() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String password = "Chat!" + suffix;
        Usuario paciente = usuario("paciente-" + suffix + "@prueba.local", "PACIENTE", password, suffix);
        Usuario medicoUsuario = usuario("medico-" + suffix + "@prueba.local", "MEDICO", password, "M" + suffix);
        Usuario intruso = usuario("intruso-" + suffix + "@prueba.local", "PACIENTE", password, "I" + suffix);
        Especialidad especialidad = especialidades.findAll().stream().findFirst().orElseThrow();

        Medico medico = new Medico();
        medico.setUsuario(medicoUsuario);
        medico.setCmp("T" + suffix);
        medico.setAniosExperiencia(1);
        medico.setTituloProfesional("Médico cirujano");
        medico.setUniversidad("Universidad de prueba");
        medico.setAnioEgreso(2020);
        medico.setRatingPromedio(0);
        medico.setTotalValoraciones(0);
        medico.setEspecialidad(especialidad);
        medico = medicos.saveAndFlush(medico);

        LocalDate fecha = LocalDate.now(LIMA).plusDays(3);
        HorarioAtencion horario = new HorarioAtencion();
        horario.setMedico(medico);
        horario.setDiaSemana(fecha.getDayOfWeek().getValue());
        horario.setHoraInicio(LocalTime.of(9, 0));
        horario.setHoraFin(LocalTime.of(12, 0));
        horario.setDuracionCitaMin(30);
        horario.setModalidad(ModalidadAtencion.AMBAS);
        horario.setActivo(true);
        horarios.saveAndFlush(horario);

        SesionWeb pacienteWeb = iniciarSesion(paciente.getEmail(), password);
        SesionWeb medicoWeb = iniciarSesion(medicoUsuario.getEmail(), password);
        SesionWeb intrusoWeb = iniciarSesion(intruso.getEmail(), password);
        String saludo = "Hola, gracias por reservar. Este es mi saludo configurado.";
        guardarMensajeInicial(medicoWeb, medico, saludo);
        assertThat(medicos.findById(medico.getId()).orElseThrow().getMensajePredeterminadoChat())
                .isEqualTo(saludo);

        String solicitud = "medicoId=" + enc(medico.getId().toString())
                + "&fecha=" + enc(fecha.toString()) + "&hora=" + enc("09:00")
                + "&modalidad=" + enc("PRESENCIAL") + "&motivo=" + enc("Verificación STOMP");
        HttpResponse<String> reserva = pacienteWeb.http().send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/appointments"))
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(pacienteWeb.csrfHeader(), pacienteWeb.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(solicitud)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(reserva.statusCode()).isEqualTo(201);
        JsonNode cuerpoReserva = objectMapper.readTree(reserva.body());
        assertThat(cuerpoReserva.path("ok").asBoolean()).isTrue();
        Cita cita = citas.findById(cuerpoReserva.path("cita").path("id").asLong()).orElseThrow();
        assertThat(cita.getEstado()).isEqualTo(EstadoCita.PENDIENTE);
        var conversacion = conversaciones.findByPaciente_IdAndMedico_Id(paciente.getId(), medico.getId()).orElseThrow();
        assertThat(mensajes.findByConversacion_IdOrderByCreadoEnAsc(conversacion.getId()))
                .extracting(m -> m.getTexto()).containsExactly(saludo);

        BlockingQueue<JsonNode> bandejaMedico = new LinkedBlockingQueue<>();
        BlockingQueue<JsonNode> bandejaPaciente = new LinkedBlockingQueue<>();
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new MappingJackson2MessageConverter());
        stomp.start();
        StompSession pacienteStomp = null;
        StompSession medicoStomp = null;
        StompSession intrusoStomp = null;
        try {
            pacienteStomp = conectar(stomp, pacienteWeb);
            medicoStomp = conectar(stomp, medicoWeb);
            intrusoStomp = conectar(stomp, intrusoWeb);
            HttpResponse<String> listaPaciente = pacienteWeb.http().send(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/api/mensajeria/conversaciones"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> listaIntruso = intrusoWeb.http().send(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/api/mensajeria/conversaciones"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(listaPaciente.statusCode()).isEqualTo(200);
            assertThat(listaPaciente.body()).contains("\"id\":" + conversacion.getId());
            assertThat(listaIntruso.statusCode()).isEqualTo(200);
            assertThat(listaIntruso.body()).isEqualTo("[]");
            suscribir(stomp, pacienteStomp, "/user/queue/mensajeria", bandejaPaciente);
            suscribir(stomp, pacienteStomp, "/user/queue/mensajeria/ack", new LinkedBlockingQueue<>());
            suscribir(stomp, medicoStomp, "/user/queue/mensajeria", bandejaMedico);
            suscribir(stomp, medicoStomp, "/user/queue/mensajeria/ack", new LinkedBlockingQueue<>());
            Thread.sleep(250);

            long mensajesAntesDeIntrusion = mensajes.count();
            enviar(intrusoStomp, conversacion.getId(), "Mensaje no autorizado", "intruso-" + suffix);
            Thread.sleep(300);
            assertThat(mensajes.count()).isEqualTo(mensajesAntesDeIntrusion);

            String clientId = "e2e-" + suffix;
            enviar(pacienteStomp, conversacion.getId(), "Buenos días, recibí el saludo.", clientId);
            JsonNode recibido = esperar(bandejaMedico, node -> node.has("message")
                    && clientId.equals(node.path("message").path("clientId").asText()));
            assertThat(recibido.path("message").path("text").asText()).isEqualTo("Buenos días, recibí el saludo.");
            assertThat(recibido.path("conversation").path("id").asLong()).isEqualTo(conversacion.getId());

            marcarLeido(medicoStomp, conversacion.getId());
            JsonNode estado = esperar(bandejaPaciente, node -> node.has("messageId")
                    && "read".equals(node.path("status").asText()));
            assertThat(estado.path("readAt").asText()).isNotBlank();
            assertThat(mensajes.findByConversacion_IdAndClientId(conversacion.getId(), clientId)
                    .orElseThrow().getLeidoEn()).isNotNull();

            escribiendo(medicoStomp, conversacion.getId(), true);
            JsonNode escribiendo = esperar(bandejaPaciente, node -> node.has("conversationId")
                    && node.has("typing"));
            assertThat(escribiendo.path("typing").asBoolean()).isTrue();

            String respuestaId = "respuesta-" + suffix;
            enviarPorRest(medicoWeb, conversacion.getId(), "Con gusto, nos vemos en la cita.", respuestaId);
            JsonNode respuesta = esperar(bandejaPaciente, node -> node.has("message")
                    && respuestaId.equals(node.path("message").path("clientId").asText()));
            assertThat(respuesta.path("message").path("text").asText())
                    .isEqualTo("Con gusto, nos vemos en la cita.");
            marcarLeidoPorRest(pacienteWeb, conversacion.getId());
            JsonNode lecturaRespuesta = esperar(bandejaMedico, node -> node.has("messageId")
                    && respuesta.path("message").path("id").asLong() == node.path("messageId").asLong()
                    && "read".equals(node.path("status").asText()));
            assertThat(lecturaRespuesta.path("readAt").asText()).isNotBlank();
            assertThat(mensajes.findByConversacion_IdAndClientId(conversacion.getId(), respuestaId)
                    .orElseThrow().getLeidoEn()).isNotNull();

            pacienteStomp.disconnect();
            pacienteStomp = conectar(stomp, pacienteWeb);
            assertThat(pacienteStomp.isConnected()).isTrue();
            HttpResponse<String> historial = pacienteWeb.http().send(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/api/mensajeria/conversaciones/" + conversacion.getId() + "/mensajes"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(historial.statusCode()).isEqualTo(200);
            assertThat(historial.body()).contains("Buenos días, recibí el saludo.",
                    "Con gusto, nos vemos en la cita.", "\"status\":\"read\"", "readAt");

            HttpResponse<String> ajeno = intrusoWeb.http().send(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/api/mensajeria/conversaciones/" + conversacion.getId() + "/mensajes"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(ajeno.statusCode()).isEqualTo(404);

            cita.setEstado(EstadoCita.CANCELADA);
            citas.saveAndFlush(cita);
            HttpResponse<String> bloqueado = pacienteWeb.http().send(HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/api/mensajeria/conversaciones/" + conversacion.getId() + "/mensajes"))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header(pacienteWeb.csrfHeader(), pacienteWeb.csrfToken())
                    .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"ya no autorizado\",\"clientId\":\"cancelado\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertThat(bloqueado.statusCode()).isEqualTo(403);
            assertThat(mensajes.findByConversacion_IdOrderByCreadoEnAsc(conversacion.getId())).hasSize(3);
        } finally {
            if (pacienteStomp != null && pacienteStomp.isConnected()) pacienteStomp.disconnect();
            if (medicoStomp != null && medicoStomp.isConnected()) medicoStomp.disconnect();
            if (intrusoStomp != null && intrusoStomp.isConnected()) intrusoStomp.disconnect();
            stomp.stop();
        }
    }

    private Usuario usuario(String email, String rol, String password, String suffix) {
        Usuario u = new Usuario();
        u.setNombres(rol.equals("MEDICO") ? "Médico" : "Usuario");
        u.setApellidos("Prueba");
        u.setDni(String.format("%08d", Math.floorMod(suffix.hashCode(), 100_000_000)));
        u.setEmail(email);
        u.setTelefono("999111222");
        u.setPasswordHash(encoder.encode(password));
        u.setEstado(EstadoUsuario.ACTIVO);
        u.setRol(roles.findByNombre(rol).orElseThrow());
        return usuarios.saveAndFlush(u);
    }

    private SesionWeb iniciarSesion(String email, String password) throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient http = HttpClient.newBuilder().cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        String loginHtml = get(http, "/login");
        String csrf = inputCsrf(loginHtml);
        String form = "email=" + enc(email) + "&password=" + enc(password) + "&_csrf=" + enc(csrf);
        HttpResponse<String> response = http.send(HttpRequest.newBuilder().uri(URI.create(baseUrl() + "/login"))
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String chatHtml = get(http, "/chat");
        String chatTag = TAG.matcher(chatHtml).results().map(java.util.regex.MatchResult::group).findFirst().orElseThrow();
        Map<String, String> attributes = attributes(chatTag);
        List<HttpCookie> found = cookies.getCookieStore().getCookies();
        String cookieHeader = found.stream().map(c -> c.getName() + "=" + c.getValue())
                .reduce((a, b) -> a + "; " + b).orElseThrow();
        return new SesionWeb(http, cookieHeader, attributes.get("data-csrf-header"),
                attributes.get("data-csrf-token"));
    }

    private void guardarMensajeInicial(SesionWeb medicoWeb, Medico medico, String saludo) throws Exception {
        String form = "nombres=" + enc("Médico") + "&apellidos=" + enc("Prueba")
                + "&especialidadId=" + enc(medico.getEspecialidad().getId().toString())
                + "&tituloProfesional=" + enc("Médico cirujano")
                + "&universidad=" + enc("Universidad de prueba")
                + "&anioEgreso=2020&aniosExperiencia=1&biografia=&mensajePredeterminadoChat=" + enc(saludo);
        HttpResponse<String> response = medicoWeb.http().send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/medico/perfil"))
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .header(medicoWeb.csrfHeader(), medicoWeb.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private String get(HttpClient http, String path) throws Exception {
        return http.send(HttpRequest.newBuilder().uri(URI.create(baseUrl() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    private String inputCsrf(String html) {
        Matcher matcher = INPUT.matcher(html);
        while (matcher.find()) {
            Map<String, String> attributes = attributes(matcher.group());
            if ("_csrf".equals(attributes.get("name"))) return attributes.get("value");
        }
        throw new AssertionError("No se encontró el token CSRF del formulario de acceso.");
    }

    private Map<String, String> attributes(String tag) {
        java.util.HashMap<String, String> values = new java.util.HashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(tag);
        while (matcher.find()) values.put(matcher.group(1), matcher.group(2));
        return values;
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private StompSession conectar(WebSocketStompClient client, SesionWeb sesion) throws Exception {
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.add(HttpHeaders.COOKIE, sesion.cookieHeader());
        StompHeaders connect = new StompHeaders();
        connect.add(sesion.csrfHeader(), sesion.csrfToken());
        URI uri = URI.create("ws://localhost:" + port + "/ws-mensajeria");
        return client.connectAsync(uri, handshake, connect, new StompSessionHandlerAdapter() { })
                .get(10, TimeUnit.SECONDS);
    }

    private void suscribir(WebSocketStompClient client, StompSession session, String destination,
                           BlockingQueue<JsonNode> queue) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.setDestination(destination);
        headers.setId("sub-" + UUID.randomUUID());
        session.subscribe(headers, new StompFrameHandler() {
            @Override
            public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((JsonNode) payload);
            }
        });
    }

    private void enviar(StompSession session, Long conversacionId, String texto, String clientId) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app/mensajeria/conversaciones/" + conversacionId + "/mensajes");
        headers.setContentType(org.springframework.util.MimeTypeUtils.APPLICATION_JSON);
        session.send(headers, Map.of("text", texto, "clientId", clientId));
    }

    private void marcarLeido(StompSession session, Long conversacionId) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app/mensajeria/conversaciones/" + conversacionId + "/leidos");
        headers.setContentType(org.springframework.util.MimeTypeUtils.APPLICATION_JSON);
        session.send(headers, Map.of());
    }

    private void enviarPorRest(SesionWeb sesion, Long conversacionId, String texto, String clientId)
            throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("text", texto, "clientId", clientId));
        HttpResponse<String> response = sesion.http().send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/mensajeria/conversaciones/" + conversacionId + "/mensajes"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(sesion.csrfHeader(), sesion.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
    }

    private void marcarLeidoPorRest(SesionWeb sesion, Long conversacionId) throws Exception {
        HttpResponse<String> response = sesion.http().send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/mensajeria/conversaciones/" + conversacionId + "/leidos"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(sesion.csrfHeader(), sesion.csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    private void escribiendo(StompSession session, Long conversacionId, boolean typing) {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app/mensajeria/conversaciones/" + conversacionId + "/escribiendo");
        headers.setContentType(org.springframework.util.MimeTypeUtils.APPLICATION_JSON);
        session.send(headers, Map.of("typing", typing));
    }

    private JsonNode esperar(BlockingQueue<JsonNode> queue, java.util.function.Predicate<JsonNode> condicion)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            JsonNode value = queue.poll(500, TimeUnit.MILLISECONDS);
            if (value != null && condicion.test(value)) return value;
        }
        throw new AssertionError("El evento STOMP esperado no llegó.");
    }

    private record SesionWeb(HttpClient http, String cookieHeader, String csrfHeader, String csrfToken) { }
}
