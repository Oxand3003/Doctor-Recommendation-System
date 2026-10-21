package utp.edu.pe.recomendaciones.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import utp.edu.pe.recomendaciones.service.CorreoService;
import utp.edu.pe.recomendaciones.service.CorreoService.Verificacion;

/**
 * Deja en el log si el correo está listo. Nunca falla el arranque ni envía correos:
 *  - siempre: una línea con el estado de la configuración (sin contraseñas ni red);
 *  - solo si medicerca.mail.verify-on-startup=true: prueba la conexión y el login SMTP.
 */
@Component
public class CorreoStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CorreoStartupCheck.class);

    private final CorreoService correo;
    private final boolean verificar;

    public CorreoStartupCheck(CorreoService correo,
                              @Value("${medicerca.mail.verify-on-startup:false}") boolean verificar) {
        this.correo = correo;
        this.verificar = verificar;
    }

    @Override
    public void run(ApplicationArguments args) {
        String falta = correo.motivoNoDisponible();
        if (falta != null) {
            log.info("Correo: envío deshabilitado. {}", falta);
            return;
        }
        log.info("Correo: configurado ({}).", correo.descripcion());
        if (verificar) {
            Verificacion v = correo.verificarConexion();
            if (v.ok()) log.info("Correo: {}", v.detalle());
            else log.warn("Correo: {}", v.detalle());
        }
    }
}
