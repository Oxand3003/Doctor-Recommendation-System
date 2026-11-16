package utp.edu.pe.recomendaciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Year;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import utp.edu.pe.recomendaciones.Controller.AdminApiController;
import utp.edu.pe.recomendaciones.dto.RegistroMedicoForm;
import utp.edu.pe.recomendaciones.dto.RegistroMedicoForm.ArchivoSustento;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Especialidad;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;

/** Formulario de registro de médicos: validaciones, especialidad escrita libremente y archivo de sustento. */
class RegistroMedicoValidacionTest extends BaseMedicosTest {

    @Autowired EspecialidadRepository especialidades;
    @Autowired AdminApiController api;

    /** Datos válidos que cada prueba modifica. */
    private static final class Datos {
        String nombres = "Lucía", apellidos = "Ramírez-Quispe", telefono = "987654321";
        String email = "registro" + ThreadLocalRandom.current().nextInt(1_000_000_000) + "@prueba.local";
        String dni = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000));
        String password = "Clave2026", confirmacion = "Clave2026";
        String cmp = String.valueOf(ThreadLocalRandom.current().nextInt(10_000, 1_000_000)), rne = "";
        String titulo = "Médico cirujano", universidad = "Universidad Nacional de Trujillo";
        Integer egreso = 2015, experiencia = 8;
        String especialidad = "Cardiología", establecimiento = "";
        ArchivoSustento sustento = constanciaPdf();

        RegistroMedicoForm form() {
            return new RegistroMedicoForm(nombres, apellidos, dni, telefono, email, password, confirmacion,
                    cmp, rne, titulo, universidad, egreso, experiencia, especialidad, establecimiento, sustento);
        }
    }

    private Datos registrar(Consumer<Datos> cambios) {
        Datos d = new Datos();
        cambios.accept(d);
        registro.registrarMedico(d.form());
        return d;
    }

    private void rechaza(Consumer<Datos> cambios, String mensaje) {
        Datos d = new Datos();
        cambios.accept(d);
        assertThatThrownBy(() -> registro.registrarMedico(d.form()))
                .isInstanceOf(NegocioException.class).hasMessageContaining(mensaje);
        assertThat(usuarios.findByEmail(d.email)).isEmpty();
    }

    private Medico medicoDe(String email) {
        return medicos.findByUsuarioId(usuarios.findByEmail(email).orElseThrow().getId()).orElseThrow();
    }

    /** La especialidad del médico, leída aparte (la del médico es un proxy perezoso fuera de la transacción). */
    private Especialidad especialidadDe(String email) {
        return especialidades.findById(medicoDe(email).getEspecialidad().getId()).orElseThrow();
    }

    @Test
    void rechazaDatosPersonalesMalEscritos() {
        rechaza(d -> d.nombres = "Lucía 2", "nombres");
        rechaza(d -> d.apellidos = "R", "apellidos");
        rechaza(d -> d.telefono = "123456789", "empiece con 9");
        rechaza(d -> d.password = d.confirmacion = "solotexto", "letras y números");
        rechaza(d -> d.confirmacion = "Otra2026", "no coinciden");
    }

    @Test
    void rechazaDatosProfesionalesInvalidos() {
        rechaza(d -> d.cmp = "CMP-12", "CMP");
        rechaza(d -> d.cmp = "1234567", "CMP");
        rechaza(d -> d.rne = "RNE12", "RNE");
        rechaza(d -> d.titulo = "12", "título profesional");
        rechaza(d -> d.egreso = Year.now().getValue() + 1, "año de egreso");
        rechaza(d -> { d.egreso = Year.now().getValue() - 3; d.experiencia = 10; }, "años desde tu egreso");
        rechaza(d -> d.establecimiento = "Clínica que no existe", "establecimiento");
    }

    @Test
    void laConstanciaEsOpcional() {
        Datos d = registrar(x -> x.sustento = null);

        Long solicitud = solicitudDe(d.email);
        assertThat(solicitudes.findById(solicitud).orElseThrow().getSustentoUrl()).isEmpty();
        assertThat(api.sustento(solicitud).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void siSeAdjuntaUnArchivoDebeSerReal() {
        // Un texto renombrado a .pdf no pasa: se revisan los bytes del archivo.
        rechaza(d -> d.sustento = new ArchivoSustento("constancia.pdf", "application/pdf", "hola".getBytes()), "Formato no permitido");
        rechaza(d -> d.sustento = new ArchivoSustento("grande.pdf", "application/pdf",
                java.util.Arrays.copyOf("%PDF-1.4".getBytes(), RegistroService.MAX_SUSTENTO + 1)), "5 MB");
    }

    @Test
    void laEspecialidadEscritaSeReconoceSinImportarTildesNiMayusculas() {
        Datos d = registrar(x -> x.especialidad = "  cardiologia ");

        assertThat(especialidadDe(d.email).getNombre()).isEqualTo("Cardiología");
    }

    @Test
    void unaEspecialidadNuevaSeCreaInactivaYSeActivaAlAprobar() {
        String nombre = "Medicina deportiva " + "abcdefghij".charAt(ThreadLocalRandom.current().nextInt(10));
        Datos d = registrar(x -> x.especialidad = nombre.toLowerCase());

        Especialidad nueva = especialidadDe(d.email);
        assertThat(nueva.getNombre()).isEqualTo(nombre.substring(0, 1).toUpperCase() + nombre.substring(1).toLowerCase());
        assertThat(nueva.isActiva()).isFalse();
        // No aparece en las listas de pacientes hasta que un administrador apruebe al médico.
        assertThat(registro.especialidades()).noneMatch(o -> o.label().equals(nueva.getNombre()));

        admin.aprobar(solicitudDe(d.email), ADMIN);

        assertThat(especialidades.findById(nueva.getId()).orElseThrow().isActiva()).isTrue();
    }

    @Test
    void elAdministradorVeElArchivoAdjuntado() {
        Datos d = registrar(x -> { });
        Long solicitud = solicitudDe(d.email);

        assertThat(solicitudes.findById(solicitud).orElseThrow().getSustentoUrl())
                .isEqualTo("/admin/api/applications/" + solicitud + "/sustento");
        ResponseEntity<byte[]> archivo = api.sustento(solicitud);
        assertThat(archivo.getStatusCode().value()).isEqualTo(200);
        assertThat(archivo.getHeaders().getContentType()).hasToString("application/pdf");
        assertThat(archivo.getHeaders().getContentDisposition().getFilename()).isEqualTo("constancia.pdf");
        assertThat(archivo.getBody()).startsWith("%PDF-".getBytes());
        assertThat(api.sustento(-1L).getStatusCode().value()).isEqualTo(404);
    }
}
