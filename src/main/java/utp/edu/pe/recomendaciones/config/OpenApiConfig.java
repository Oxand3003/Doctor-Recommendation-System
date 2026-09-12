package utp.edu.pe.recomendaciones.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 
 * Metadatos de la documentacion Swagger / OpenAPI.

 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("API - Sistema de Recomendacion de Medicos")
                        .version("1.0.0")
                        .description("Primer avance. Recomienda medicos del sistema de salud peruano "
                                + "por especialidad, rating y experiencia.")
                        .contact(new Contact().name("Equipo de desarrollo")));
    }
}
