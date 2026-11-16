package utp.edu.pe.recomendaciones.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import utp.edu.pe.recomendaciones.repository.ConsultaRepository;

/** Un visitante sin sesión puede enviar el formulario "¿Necesitas ayuda con tu cuenta?" de la página de inicio. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"medicerca.demo-data=false"})
class FormularioContactoIntegrationTest {

    private static final Pattern FORM = Pattern.compile("<form[^>]*id=\"contact-form\".*?</form>", Pattern.DOTALL);
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    @LocalServerPort int port;
    @Autowired ConsultaRepository consultas;

    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void visitanteSinSesionEnviaLaConsulta() throws Exception {
        String home = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).build(),
                HttpResponse.BodyHandlers.ofString()).body();
        Matcher form = FORM.matcher(home);
        assertThat(form.find()).as("formulario de contacto en la página de inicio").isTrue();
        Matcher csrf = CSRF.matcher(form.group());
        assertThat(csrf.find()).as("el formulario lleva el token CSRF").isTrue();

        long antes = consultas.count();
        String cuerpo = "_csrf=" + enc(csrf.group(1)) + "&nombres=" + enc("Ana Pérez") + "&correo=ana%40correo.com"
                + "&telefono=987654321&motivo=PROBLEMA_CUENTA&sitioWeb="
                + "&mensaje=" + enc("No puedo entrar a mi cuenta desde ayer.");
        HttpResponse<String> envio = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/consultas"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(), HttpResponse.BodyHandlers.ofString());

        assertThat(envio.statusCode()).isEqualTo(302);
        assertThat(envio.headers().firstValue("Location").orElse("")).endsWith("/#contacto");
        assertThat(consultas.count()).isEqualTo(antes + 1);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
