package utp.edu.pe.recomendaciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.service.CorreoService.Verificacion;

/** Pruebas sin red ni Spring: comprueban la configuración y cómo se arma el mensaje; no se envía nada. */
class CorreoServiceTest {

    /** Remitente de prueba: guarda el mensaje en vez de enviarlo por SMTP. */
    static class Captura extends JavaMailSenderImpl {
        MimeMessage enviado;

        @Override
        public void send(MimeMessage mensaje) {
            this.enviado = mensaje;
        }
    }

    private static Captura sender(String usuario, String clave) {
        Captura c = new Captura();
        c.setHost("smtp.gmail.com");
        c.setPort(587);
        c.setUsername(usuario);
        c.setPassword(clave);
        return c;
    }

    private static CorreoService servicio(JavaMailSender s, String from) {
        ObjectProvider<JavaMailSender> p = s == null
                ? new StaticListableBeanFactory().getBeanProvider(JavaMailSender.class)
                : new StaticListableBeanFactory(Map.of("mailSender", s)).getBeanProvider(JavaMailSender.class);
        return new CorreoService(p, from, "MediCerca");
    }

    @Test
    void sinServidorSmtpNoEstaDisponible() {
        CorreoService c = servicio(null, "");
        assertThat(c.disponible()).isFalse();
        assertThat(c.verificarConexion().estado()).isEqualTo(Verificacion.Estado.NO_CONFIGURADO);
    }

    @Test
    void sinCredencialesNoEstaDisponibleYNoEnvia() {
        CorreoService c = servicio(sender("", ""), "");
        assertThat(c.disponible()).isFalse();
        assertThat(c.motivoNoDisponible()).contains("MAIL_USERNAME");
        assertThat(c.verificarConexion().estado()).isEqualTo(Verificacion.Estado.NO_CONFIGURADO);
        assertThatThrownBy(() -> c.enviarTexto("a@b.com", "Hola", "cuerpo")).isInstanceOf(NegocioException.class);
    }

    @Test
    void sinContrasenaPideLaContrasenaDeAplicacion() {
        CorreoService c = servicio(sender("medico@gmail.com", ""), "");
        assertThat(c.motivoNoDisponible()).contains("MAIL_PASSWORD");
    }

    @Test
    void conCredencialesArmaElMensajeSinEnviarloPorRed() throws Exception {
        Captura s = sender("medicerca@gmail.com", "abcdefghijklmnop");
        CorreoService c = servicio(s, "");
        assertThat(c.disponible()).isTrue();

        c.enviarTexto("paciente@correo.com", "Código de seguridad\r\nBcc: otro@x.com", "Tu código es 123456");

        assertThat(s.enviado).isNotNull();
        InternetAddress de = (InternetAddress) s.enviado.getFrom()[0];
        assertThat(de.getAddress()).isEqualTo("medicerca@gmail.com");   // sin MAIL_FROM usa el usuario
        assertThat(de.getPersonal()).isEqualTo("MediCerca");
        assertThat(s.enviado.getAllRecipients()[0].toString()).isEqualTo("paciente@correo.com");
        assertThat(s.enviado.getSubject()).isEqualTo("Código de seguridad Bcc: otro@x.com"); // sin saltos de línea
        assertThat(s.enviado.getHeader("Bcc")).isNull();
        assertThat(s.enviado.getContent()).isEqualTo("Tu código es 123456");
    }

    @Test
    void usaMailFromCuandoSeConfigura() throws Exception {
        Captura s = sender("medicerca@gmail.com", "abcdefghijklmnop");
        servicio(s, "avisos@medicerca.pe").enviarTexto("p@c.com", "Hola", "x");
        assertThat(((InternetAddress) s.enviado.getFrom()[0]).getAddress()).isEqualTo("avisos@medicerca.pe");
    }

    @Test
    void laDescripcionNoRevelaLaContrasena() {
        CorreoService c = servicio(sender("medicerca@gmail.com", "abcdefghijklmnop"), "");
        assertThat(c.descripcion()).contains("smtp.gmail.com:587").doesNotContain("abcdefghijklmnop")
                .doesNotContain("medicerca@gmail.com");
    }

    @Test
    void enmascararOcultaElUsuario() {
        assertThat(CorreoService.enmascarar("ana.perez@gmail.com")).isEqualTo("a***@gmail.com");
        assertThat(CorreoService.enmascarar("")).isEqualTo("(sin definir)");
    }
}
