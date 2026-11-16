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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import utp.edu.pe.recomendaciones.model.Distrito;
import utp.edu.pe.recomendaciones.model.Establecimiento;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.repository.DistritoRepository;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;
import utp.edu.pe.recomendaciones.repository.EstablecimientoRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.repository.RolRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

/**
 * Página de inicio con sesiones reales: a dónde llega el paciente al iniciar sesión, el botón flotante del
 * asistente de IA y las sedes del buscador con su distrito.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"medicerca.demo-data=false"})
class PaginaInicioIntegrationTest {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");
    private static final Pattern FAB = Pattern.compile("<a[^>]*id=\"ai-fab\"[^>]*>");

    @LocalServerPort int port;
    @Autowired RolRepository roles;
    @Autowired UsuarioRepository usuarios;
    @Autowired MedicoRepository medicos;
    @Autowired EspecialidadRepository especialidades;
    @Autowired DistritoRepository distritos;
    @Autowired EstablecimientoRepository establecimientos;
    @Autowired PasswordEncoder encoder;

    @Test
    void elVisitanteVeElBotonDelAsistenteQueLoLlevaAlLogin() throws Exception {
        String home = get(cliente(), "/").body();

        Matcher fab = FAB.matcher(home);
        assertThat(fab.find()).as("botón flotante del asistente").isTrue();
        assertThat(fab.group()).contains("href=\"/login\"");
    }

    @Test
    void elPacienteEntraAlBuscadorConElAsistenteAbiertoYLasSedesTienenSuDistrito() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String password = "Inicio!" + suffix;
        Usuario paciente = usuario("paciente-" + suffix + "@prueba.local", "PACIENTE", password, suffix);
        Distrito huanchaco = distritos.findAll().stream().filter(d -> "Huanchaco".equals(d.getNombre()))
                .findFirst().orElseThrow();
        medicoEnSede("Clínica Prueba " + suffix, huanchaco, "M" + suffix, password);

        HttpClient http = cliente();
        String csrf = csrf(get(http, "/login").body());
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(URI.create(base() + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("email=" + enc(paciente.getEmail())
                        + "&password=" + enc(password) + "&_csrf=" + enc(csrf))).build(),
                HttpResponse.BodyHandlers.ofString());

        // Ya no aterriza en "Mi cuenta" (corta, quedaba mirando el pie de página) sino en el buscador.
        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(login.headers().firstValue("Location").orElse("")).endsWith("/#patient-search");

        String home = get(http, "/").body();
        assertThat(home).contains("id=\"patient-search\"");
        // Sin ?modo, la pestaña abierta es la del asistente de IA.
        assertThat(home).containsPattern("id=\"ai-search-panel\" class=\"finder-panel\"\\s+role=\"tabpanel\"(?![^>]*is-hidden)");
        assertThat(home).containsPattern("id=\"manual-search-panel\" class=\"finder-panel[^\"]*is-hidden");
        Matcher fab = FAB.matcher(home);
        assertThat(fab.find()).isTrue();
        assertThat(fab.group()).contains("href=\"/?modo=ia#patient-search\"");
        assertThat(home).contains("<option value=\"Clínica Prueba " + suffix + "\" data-district=\"Huanchaco\">");

        // "Mi cuenta" solo tiene los datos personales; las citas y el historial están en /mis-citas.
        assertThat(home).contains("href=\"/mis-citas\">Mis citas</a>");
        String cuenta = get(http, "/account").body();
        assertThat(cuenta).contains("Mis datos").doesNotContain("id=\"my-appointments\"").doesNotContain("id=\"clinical-history\"");
        HttpResponse<String> citas = get(http, "/mis-citas");
        assertThat(citas.statusCode()).isEqualTo(200);
        assertThat(citas.body()).contains("id=\"my-appointments\"").contains("id=\"clinical-history\"").doesNotContain("Mis datos");
        // Enlaces antiguos al buscador que estaba en Mi cuenta.
        HttpResponse<String> antiguo = get(http, "/account?modo=ia");
        assertThat(antiguo.statusCode()).isEqualTo(302);
        assertThat(antiguo.headers().firstValue("Location").orElse("")).endsWith("/?modo=ia#patient-search");
    }

    private void medicoEnSede(String sede, Distrito distrito, String suffix, String password) {
        Medico medico = new Medico();
        medico.setUsuario(usuario("medico-" + suffix + "@prueba.local", "MEDICO", password, suffix));
        medico.setCmp("T" + suffix);
        medico.setAniosExperiencia(1);
        medico.setTituloProfesional("Médico cirujano");
        medico.setUniversidad("Universidad de prueba");
        medico.setAnioEgreso(2020);
        medico.setEspecialidad(especialidades.findAll().stream().findFirst().orElseThrow());
        Establecimiento clinica = new Establecimiento();
        clinica.setNombre(sede);
        clinica.setDistrito(distrito);
        medico.setEstablecimiento(establecimientos.saveAndFlush(clinica));
        medicos.saveAndFlush(medico);
    }

    private Usuario usuario(String email, String rol, String password, String suffix) {
        Usuario u = new Usuario();
        u.setNombres("Usuario");
        u.setApellidos("Prueba");
        u.setDni(String.format("%08d", Math.floorMod(suffix.hashCode(), 100_000_000)));
        u.setEmail(email);
        u.setTelefono("999111222");
        u.setPasswordHash(encoder.encode(password));
        u.setEstado(EstadoUsuario.ACTIVO);
        u.setRol(roles.findByNombre(rol).orElseThrow());
        return usuarios.saveAndFlush(u);
    }

    private HttpClient cliente() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    private HttpResponse<String> get(HttpClient http, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base() + path)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String csrf(String html) {
        Matcher m = CSRF.matcher(html);
        assertThat(m.find()).as("token CSRF del login").isTrue();
        return m.group(1);
    }

    private String base() {
        return "http://localhost:" + port;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
