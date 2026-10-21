package utp.edu.pe.recomendaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RecomendacionCitasApplication {

	public static void main(String[] args) {
		SpringApplication.run(RecomendacionCitasApplication.class, args);
	}

}
