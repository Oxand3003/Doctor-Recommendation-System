package utp.edu.pe.recomendaciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.CodigoSeguridad;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.PropositoCodigo;
import utp.edu.pe.recomendaciones.repository.CodigoSeguridadRepository;

/** Cambio de contraseña y de correo con código de seguridad: flujo correcto, caducidad, intentos y permisos. */
class CredencialesMedicoServiceTest extends BaseMedicosTest {

    private static final String NUEVA = "NuevaClave456!";

    @Autowired CredencialesMedicoService credenciales;
    @Autowired CodigoSeguridadRepository codigos;

    private Usuario usuario(String email) {
        return usuarios.findByEmail(email).orElseThrow();
    }

    /** Códigos guardados de ese médico (se lee dentro de una transacción porque el usuario es una relación perezosa). */
    private List<CodigoSeguridad> codigosDe(String email) {
        Long uid = usuario(email).getId();
        return new TransactionTemplate(transacciones).execute(status -> codigos.findAll().stream()
                .filter(c -> c.getUsuario().getId().equals(uid)).toList());
    }

    // ------------------------------------------------------------------ contraseña
    @Test
    void cambiarContrasenaConCodigoCorrectoLaGuardaEnLaBaseDeDatos() {
        String email = medicoActivo();
        credenciales.solicitarCodigoPassword(email);
        String codigo = ultimoCodigoEnviadoA(email); // el código llegó al correo registrado

        credenciales.cambiarPassword(email, codigo, NUEVA, NUEVA);

        String hash = usuario(email).getPasswordHash();
        assertThat(encoder.matches(NUEVA, hash)).isTrue();
        assertThat(encoder.matches(PASSWORD_INICIAL, hash)).isFalse();
        // El código es de un solo uso
        assertThatThrownBy(() -> credenciales.cambiarPassword(email, codigo, "OtraClave789!", "OtraClave789!"))
                .isInstanceOf(NegocioException.class).hasMessageContaining("Primero solicita");
        assertThat(encoder.matches(NUEVA, usuario(email).getPasswordHash())).isTrue();
    }

    @Test
    void sinCodigoPedidoNoSePuedeCambiarLaContrasena() {
        String email = medicoActivo();

        assertThatThrownBy(() -> credenciales.cambiarPassword(email, "123456", NUEVA, NUEVA))
                .isInstanceOf(NegocioException.class).hasMessageContaining("Primero solicita");
        assertThat(encoder.matches(PASSWORD_INICIAL, usuario(email).getPasswordHash())).isTrue();
    }

    @Test
    void elCodigoSeAnulaTrasLosIntentosFallidosYNoSirveNiElCorrecto() {
        String email = medicoActivo();
        credenciales.solicitarCodigoPassword(email);
        String bueno = ultimoCodigoEnviadoA(email);
        String malo = codigoDistintoDe(bueno);

        // Los intentos fallidos se guardan aunque cada llamada lance excepción
        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> credenciales.cambiarPassword(email, malo, NUEVA, NUEVA))
                    .isInstanceOf(NegocioException.class).hasMessageContaining("incorrecto");
        }
        assertThatThrownBy(() -> credenciales.cambiarPassword(email, malo, NUEVA, NUEVA))
                .isInstanceOf(NegocioException.class).hasMessageContaining("se anuló");

        assertThatThrownBy(() -> credenciales.cambiarPassword(email, bueno, NUEVA, NUEVA))
                .isInstanceOf(NegocioException.class).hasMessageContaining("Primero solicita");
        assertThat(encoder.matches(PASSWORD_INICIAL, usuario(email).getPasswordHash())).isTrue();
    }

    @Test
    void unCodigoCaducadoSeRechaza() {
        String email = medicoActivo();
        credenciales.solicitarCodigoPassword(email);
        String codigo = ultimoCodigoEnviadoA(email);
        Long uid = usuario(email).getId();
        // Se hace vencer el código de ESTE médico
        new TransactionTemplate(transacciones).executeWithoutResult(status -> codigos.findAll().stream()
                .filter(c -> c.getUsuario().getId().equals(uid) && !c.isConsumido())
                .forEach(c -> c.setExpiraEn(LocalDateTime.now().minusMinutes(1))));

        assertThatThrownBy(() -> credenciales.cambiarPassword(email, codigo, NUEVA, NUEVA))
                .isInstanceOf(NegocioException.class).hasMessageContaining("caducó");
        assertThat(encoder.matches(PASSWORD_INICIAL, usuario(email).getPasswordHash())).isTrue();
        assertThat(credenciales.estado(email).passwordCodigoPendiente()).isFalse();
    }

    @Test
    void validaLaNuevaContrasenaSinGastarIntentos() {
        String email = medicoActivo();
        credenciales.solicitarCodigoPassword(email);
        String codigo = ultimoCodigoEnviadoA(email);

        assertThatThrownBy(() -> credenciales.cambiarPassword(email, codigo, "corta", "corta"))
                .isInstanceOf(NegocioException.class).hasMessageContaining("8 caracteres");
        assertThatThrownBy(() -> credenciales.cambiarPassword(email, codigo, NUEVA, "Distinta123!"))
                .isInstanceOf(NegocioException.class).hasMessageContaining("no coinciden");
        assertThatThrownBy(() -> credenciales.cambiarPassword(email, codigo, PASSWORD_INICIAL, PASSWORD_INICIAL))
                .isInstanceOf(NegocioException.class).hasMessageContaining("distinta");

        // El código sigue intacto y funciona
        credenciales.cambiarPassword(email, codigo, NUEVA, NUEVA);
        assertThat(encoder.matches(NUEVA, usuario(email).getPasswordHash())).isTrue();
    }

    // ------------------------------------------------------------------ correo
    @Test
    void cambiarCorreoEnviaElCodigoAlCorreoActualYGuardaElNuevo() {
        String actual = medicoActivo();
        String nuevo = "nuevo" + System.nanoTime() + "@prueba.local";

        credenciales.solicitarCodigoEmail(actual, nuevo.toUpperCase(), nuevo);
        String codigo = ultimoCodigoEnviadoA(actual); // identidad: llega al correo ACTUAL
        verify(correo, never()).enviarHtml(eq(nuevo), anyString(), anyString());
        assertThat(credenciales.estado(actual).nuevoEmailPendiente()).isEqualTo(nuevo);

        String guardado = credenciales.cambiarEmail(actual, codigo);

        assertThat(guardado).isEqualTo(nuevo);
        assertThat(usuarios.findByEmail(nuevo)).isPresent();
        assertThat(usuarios.findByEmail(actual)).isEmpty();
        verify(correo).enviarHtml(eq(nuevo), anyString(), anyString());   // bienvenida al correo nuevo
    }

    @Test
    void rechazaCorreosInvalidosRepetidosOYaRegistrados() {
        String actual = medicoActivo();
        String otroMedico = medicoActivo();

        assertThatThrownBy(() -> credenciales.solicitarCodigoEmail(actual, "no-es-correo", "no-es-correo"))
                .isInstanceOf(NegocioException.class).hasMessageContaining("no es válido");
        assertThatThrownBy(() -> credenciales.solicitarCodigoEmail(actual, "a@prueba.local", "b@prueba.local"))
                .isInstanceOf(NegocioException.class).hasMessageContaining("no coinciden");
        assertThatThrownBy(() -> credenciales.solicitarCodigoEmail(actual, actual, actual))
                .isInstanceOf(NegocioException.class).hasMessageContaining("igual");
        assertThatThrownBy(() -> credenciales.solicitarCodigoEmail(actual, otroMedico, otroMedico))
                .isInstanceOf(NegocioException.class).hasMessageContaining("ya está registrado");
        assertThatThrownBy(() -> credenciales.solicitarCodigoEmail(actual, "carlos.mendoza@correo.com", "carlos.mendoza@correo.com"))
                .isInstanceOf(NegocioException.class).hasMessageContaining("ya está registrado");
        verify(correo, never()).enviarHtml(eq(actual), anyString(), anyString());
    }

    @Test
    void siOtraCuentaTomaElCorreoAntesDeConfirmarNoSeCambiaNada() {
        String actual = medicoActivo();
        String libre = "libre" + System.nanoTime() + "@prueba.local";
        credenciales.solicitarCodigoEmail(actual, libre, libre);
        String codigo = ultimoCodigoEnviadoA(actual);

        // Mientras tanto, otro médico cambia SU correo a ese mismo (lo toma primero)
        String competidor = medicoActivo();
        credenciales.solicitarCodigoEmail(competidor, libre, libre);
        credenciales.cambiarEmail(competidor, ultimoCodigoEnviadoA(competidor));

        assertThatThrownBy(() -> credenciales.cambiarEmail(actual, codigo))
                .isInstanceOf(NegocioException.class).hasMessageContaining("ya está registrado");
        assertThat(usuarios.findByEmail(actual)).isPresent();
    }

    // ------------------------------------------------------------------ permisos
    @Test
    void elCodigoDeUnMedicoNoSirveParaOtraCuenta() {
        String a = medicoActivo();
        String b = medicoActivo();
        credenciales.solicitarCodigoPassword(a);
        String codigoDeA = ultimoCodigoEnviadoA(a);

        assertThatThrownBy(() -> credenciales.cambiarPassword(b, codigoDeA, NUEVA, NUEVA))
                .isInstanceOf(NegocioException.class).hasMessageContaining("Primero solicita");

        assertThat(encoder.matches(PASSWORD_INICIAL, usuario(a).getPasswordHash())).isTrue();
        assertThat(encoder.matches(PASSWORD_INICIAL, usuario(b).getPasswordHash())).isTrue();
    }

    @Test
    void soloUnMedicoActivoPuedePedirCodigos() {
        assertThatThrownBy(() -> credenciales.solicitarCodigoPassword("carlos.mendoza@correo.com")) // paciente
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> credenciales.solicitarCodigoPassword(ADMIN))                       // administrador
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> credenciales.solicitarCodigoPassword(registrarPendiente()))       // médico sin aprobar
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> credenciales.solicitarCodigoPassword("nadie@prueba.local"))        // cuenta inexistente
                .isInstanceOf(ResponseStatusException.class);
        verify(correo, never()).enviarHtml(anyString(), anyString(), anyString());
    }

    // ------------------------------------------------------------------ límites y fallos de envío
    @Test
    void noPermitePedirOtroCodigoAntesDelTiempoMinimo() {
        String email = medicoActivo();
        credenciales.solicitarCodigoPassword(email);

        assertThatThrownBy(() -> credenciales.solicitarCodigoPassword(email))
                .isInstanceOf(NegocioException.class).hasMessageContaining("Espera");
    }

    @Test
    void ponUnTopeDeCodigosPorHora() {
        String email = medicoActivo();
        Usuario u = usuario(email);
        for (int i = 0; i < 5; i++) { // cinco códigos ya emitidos hace 30 minutos
            CodigoSeguridad c = new CodigoSeguridad();
            c.setUsuario(u);
            c.setProposito(PropositoCodigo.CAMBIO_PASSWORD);
            c.setCodigoHash(encoder.encode("123456"));
            c.setConsumido(true);
            c.setCreadoEn(LocalDateTime.now().minusMinutes(30));
            c.setExpiraEn(LocalDateTime.now().minusMinutes(20));
            codigos.save(c);
        }

        assertThatThrownBy(() -> credenciales.solicitarCodigoPassword(email))
                .isInstanceOf(NegocioException.class).hasMessageContaining("demasiados códigos");
    }

    @Test
    void siElCorreoNoSeEnviaNoQuedaUnCodigoPendiente() {
        doThrow(new NegocioException("No se pudo enviar el correo.")).when(correo)
                .enviarHtml(anyString(), anyString(), anyString());
        String email = medicoActivo();

        assertThatThrownBy(() -> credenciales.solicitarCodigoPassword(email)).isInstanceOf(NegocioException.class);

        assertThat(credenciales.estado(email).passwordCodigoPendiente()).isFalse();
    }

    @Test
    void elCodigoSeGuardaSoloComoHash() {
        String email = medicoActivo();
        credenciales.solicitarCodigoPassword(email);
        String codigo = ultimoCodigoEnviadoA(email);

        List<CodigoSeguridad> mios = codigosDe(email);

        assertThat(mios).hasSize(1);
        assertThat(mios.get(0).getCodigoHash()).isNotEqualTo(codigo).startsWith("$2");
    }
}
