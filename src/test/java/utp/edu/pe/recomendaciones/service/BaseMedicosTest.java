package utp.edu.pe.recomendaciones.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import utp.edu.pe.recomendaciones.RecomendacionCitasApplication;
import utp.edu.pe.recomendaciones.dto.RegistroMedicoForm;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.repository.SolicitudVerificacionRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

/**
 * Base de las pruebas de la parte 2: levanta la aplicación completa (H2 en memoria, datos de demostración) con
 * CorreoService reemplazado por un doble que NO envía nada y deja leer lo que se habría enviado. Cada prueba crea
 * sus propios médicos con correo/DNI/CMP únicos, así que no dependen unas de otras.
 */
@SpringBootTest(classes = RecomendacionCitasApplication.class)
abstract class BaseMedicosTest {

    static final String ADMIN = "admin@test.local";
    static final String PASSWORD_INICIAL = "Inicial123!";
    private static final Pattern CODIGO = Pattern.compile(">(\\d{6})<");

    @Autowired protected RegistroService registro;
    @Autowired protected AdminService admin;
    @Autowired protected UsuarioRepository usuarios;
    @Autowired protected MedicoRepository medicos;
    @Autowired protected SolicitudVerificacionRepository solicitudes;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected PlatformTransactionManager transacciones;

    @MockitoBean protected CorreoService correo;

    /** Registra un médico (queda PENDIENTE con su solicitud) y devuelve su correo. */
    protected String registrarPendiente() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String email = "medico" + r.nextInt(1_000_000_000) + "@prueba.local";
        Long especialidad = registro.especialidades().get(0).id();
        registro.registrarMedico(new RegistroMedicoForm(
                "Prueba", "Medico", String.valueOf(r.nextInt(10_000_000, 100_000_000)), "999111222",
                email, PASSWORD_INICIAL,
                "T" + r.nextInt(100_000, 1_000_000), null, "Médico cirujano", "UNMSM", 2010, 5,
                especialidad, null, "https://ejemplo.com/sustento"));
        return email;
    }

    /** Registra un médico y lo aprueba: cuenta ACTIVA lista para usar. */
    protected String medicoActivo() {
        String email = registrarPendiente();
        admin.aprobar(solicitudDe(email), ADMIN);
        return email;
    }

    protected Long solicitudDe(String email) {
        return new TransactionTemplate(transacciones).execute(status -> solicitudes.findAll().stream()
                .filter(s -> s.getMedico().getUsuario().getEmail().equals(email))
                .findFirst().orElseThrow().getId());
    }

    /** El código de 6 dígitos del último correo que se "envió" a esa dirección. */
    protected String ultimoCodigoEnviadoA(String destino) {
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(correo, atLeastOnce()).enviarHtml(eq(destino), anyString(), html.capture());
        Matcher m = CODIGO.matcher(html.getValue());
        if (!m.find()) throw new AssertionError("El correo enviado no contiene un código de 6 dígitos");
        return m.group(1);
    }

    protected static String codigoDistintoDe(String codigo) {
        return "000000".equals(codigo) ? "111111" : "000000";
    }
}
