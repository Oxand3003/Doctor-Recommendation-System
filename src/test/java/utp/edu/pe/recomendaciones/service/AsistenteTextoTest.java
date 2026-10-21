package utp.edu.pe.recomendaciones.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/** El asistente debe entender síntomas, fechas y horas aunque estén mal escritos (sin Spring ni base de datos). */
class AsistenteTextoTest {

    private static final List<String> ESPECIALIDADES = List.of("Cardiología", "Pediatría", "Dermatología",
            "Neurología", "Traumatología y Ortopedia", "Psiquiatría", "Gastroenterología", "Oftalmología",
            "Medicina General", "Odontología");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 18);   // domingo

    private String especialidadPara(String texto) {
        var sugerencias = CatalogoSintomas.sugerir(texto, ESPECIALIDADES);
        return sugerencias.isEmpty() ? null : sugerencias.get(0).especialidad();
    }

    @Test
    void sintomasMalEscritosOrientanALaEspecialidad() {
        assertThat(especialidadPara("me duele mucho la cavesa")).isEqualTo("Neurología");
        assertThat(especialidadPara("tengo migrña y mareos")).isEqualTo("Neurología");
        assertThat(especialidadPara("me duele el estomgo y tengo diarrea")).isEqualTo("Gastroenterología");
        assertThat(especialidadPara("tengo acne y granos")).isEqualTo("Dermatología");
        assertThat(especialidadPara("mi ijo tiene fiebre")).isEqualTo("Pediatría");
        assertThat(especialidadPara("me duele la rodiya")).isEqualTo("Traumatología y Ortopedia");
        assertThat(especialidadPara("bista borrosa")).isEqualTo("Oftalmología");
        assertThat(especialidadPara("tengo ancsiedad y no puedo dormir")).isEqualTo("Psiquiatría");
        assertThat(especialidadPara("me duele una muela")).isEqualTo("Odontología");
        assertThat(especialidadPara("palpitasiones y dolor de pecho")).isEqualTo("Cardiología");
        assertThat(especialidadPara("tengo fiebre y tos")).isEqualTo("Medicina General");
    }

    @Test
    void textoSinSintomasNoSugiereNada() {
        assertThat(especialidadPara("hola buenas")).isNull();
        assertThat(especialidadPara("mañana a las 4")).isNull();
        // "marzo" no debe confundirse con "mareo".
        assertThat(especialidadPara("el 5 de marzo")).isNull();
    }

    @Test
    void reconoceEspecialidadesEscritasPorNombre() {
        assertThat(CatalogoSintomas.porNombre("busco un cardiolgo", ESPECIALIDADES)).isEqualTo("Cardiología");
        assertThat(CatalogoSintomas.porNombre("quiero dermatolojia", ESPECIALIDADES)).isEqualTo("Dermatología");
        assertThat(CatalogoSintomas.porNombre("medisina jeneral", ESPECIALIDADES)).isEqualTo("Medicina General");
    }

    @Test
    void avisaCuandoPuedeSerUrgente() {
        assertThat(CatalogoSintomas.pareceUrgente("tengo dolor de pecho y me falta el aire")).isTrue();
        assertThat(CatalogoSintomas.pareceUrgente("me duele la muela")).isFalse();
    }

    @Test
    void fechasEscritasLibremente() {
        assertThat(InterpreteFechas.fecha("mañana", HOY)).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(InterpreteFechas.fecha("manana", HOY)).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(InterpreteFechas.fecha("pasado mañana", HOY)).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(InterpreteFechas.fecha("hoy", HOY)).isEqualTo(HOY);
        assertThat(InterpreteFechas.fecha("el viernez", HOY)).isEqualTo(LocalDate.of(2026, 10, 23));
        assertThat(InterpreteFechas.fecha("25 de octbre", HOY)).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(InterpreteFechas.fecha("5 de julio", HOY)).isEqualTo(LocalDate.of(2027, 7, 5));
        assertThat(InterpreteFechas.fecha("20/10", HOY)).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(InterpreteFechas.fecha("el 25", HOY)).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(InterpreteFechas.fecha("dentro de 3 dias", HOY)).isEqualTo(LocalDate.of(2026, 10, 21));
        // "de la mañana" es una hora, no el día siguiente.
        assertThat(InterpreteFechas.fecha("a las 9 de la mañana", HOY)).isNull();
    }

    @Test
    void horasEscritasLibremente() {
        assertThat(InterpreteFechas.hora("a las 4")).isEqualTo(LocalTime.of(16, 0));
        assertThat(InterpreteFechas.hora("4pm")).isEqualTo(LocalTime.of(16, 0));
        assertThat(InterpreteFechas.hora("16:30")).isEqualTo(LocalTime.of(16, 30));
        assertThat(InterpreteFechas.hora("a las 4 y media")).isEqualTo(LocalTime.of(16, 30));
        assertThat(InterpreteFechas.hora("a las 9 de la mañana")).isEqualTo(LocalTime.of(9, 0));
        assertThat(InterpreteFechas.hora("por la tarde")).isEqualTo(LocalTime.of(13, 0));
        assertThat(InterpreteFechas.hora("mañana")).isNull();
    }

    @Test
    void palabrasConErroresDeOrtografiaCoinciden() {
        assertThat(AsistenteTexto.parecida(AsistenteTexto.fonetica("cavesa"), AsistenteTexto.fonetica("cabeza"))).isTrue();
        assertThat(AsistenteTexto.parecida(AsistenteTexto.fonetica("ojo"), AsistenteTexto.fonetica("oso"))).isFalse();
    }
}
