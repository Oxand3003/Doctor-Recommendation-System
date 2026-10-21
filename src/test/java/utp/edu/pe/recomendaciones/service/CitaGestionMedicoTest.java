package utp.edu.pe.recomendaciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import utp.edu.pe.recomendaciones.dto.CitaCancelada;
import utp.edu.pe.recomendaciones.dto.CitaDetalleView;
import utp.edu.pe.recomendaciones.dto.RegistroPacienteForm;
import utp.edu.pe.recomendaciones.dto.ReservaCreada;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Cita;
import utp.edu.pe.recomendaciones.model.HorarioAtencion;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.enums.EstadoCita;
import utp.edu.pe.recomendaciones.model.enums.ModalidadAtencion;
import utp.edu.pe.recomendaciones.repository.CitaRepository;
import utp.edu.pe.recomendaciones.repository.HorarioAtencionRepository;

/**
 * El médico gestiona una reserva: WhatsApp al número del paciente, eliminar (cancelar) con aviso por correo, y el
 * correo de confirmación al reservar. CorreoService está simulado (BaseMedicosTest), así que se verifica qué se envía.
 */
class CitaGestionMedicoTest extends BaseMedicosTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final LocalDate manana = LocalDate.now(LIMA).plusDays(1);

    @Autowired CitaPacienteService reservas;
    @Autowired AgendaMedicoService agenda;
    @Autowired NotificacionCitaService notificaciones;
    @Autowired CitaRepository citaRepo;
    @Autowired HorarioAtencionRepository horarioRepo;

    private String pacienteNuevo(String telefono) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String email = "paciente" + r.nextInt(1_000_000_000) + "@prueba.local";
        registro.registrarPaciente(new RegistroPacienteForm("Ana", "Paciente",
                String.valueOf(r.nextInt(10_000_000, 100_000_000)), telefono, email, PASSWORD_INICIAL));
        return email;
    }

    private String medicoConAgenda() {
        String email = medicoActivo();
        new TransactionTemplate(transacciones).execute(status -> {
            Medico m = medicos.findByUsuarioId(usuarios.findByEmail(email).orElseThrow().getId()).orElseThrow();
            for (int dia = 1; dia <= 7; dia++) {
                HorarioAtencion h = new HorarioAtencion();
                h.setMedico(m);
                h.setDiaSemana(dia);
                h.setHoraInicio(LocalTime.of(9, 0));
                h.setHoraFin(LocalTime.of(12, 0));
                h.setDuracionCitaMin(30);
                h.setModalidad(ModalidadAtencion.AMBAS);
                h.setActivo(true);
                horarioRepo.save(h);
            }
            return null;
        });
        return email;
    }

    private Long medicoId(String email) {
        return medicos.findByUsuarioId(usuarios.findByEmail(email).orElseThrow().getId()).orElseThrow().getId();
    }

    private ReservaCreada reservar(String paciente, String medicoEmail, String hora) {
        return reservas.reservar(paciente, String.valueOf(medicoId(medicoEmail)), manana.toString(), hora,
                "PRESENCIAL", "Chequeo");
    }

    @Test
    void elDetalleTraeElEnlaceDeWhatsappConElTelefonoDelPaciente() {
        String medico = medicoConAgenda();
        ReservaCreada r = reservar(pacienteNuevo("987 654 321"), medico, "09:00");

        CitaDetalleView d = agenda.detalle(medico, r.id());

        assertThat(d.whatsappUrl()).startsWith("https://wa.me/51987654321?text=").contains(r.codigo());
        assertThat(d.eliminable()).isTrue();
    }

    @Test
    void sinTelefonoUtilNoHayEnlaceDeWhatsapp() {
        assertThat(AgendaMedicoService.numeroWhatsapp("987 654 321")).isEqualTo("51987654321");
        assertThat(AgendaMedicoService.numeroWhatsapp("+51 987654321")).isEqualTo("51987654321");
        assertThat(AgendaMedicoService.numeroWhatsapp("+1 (555) 123-4567")).isEqualTo("15551234567");
        assertThat(AgendaMedicoService.numeroWhatsapp("12345")).isNull();
        assertThat(AgendaMedicoService.numeroWhatsapp("—")).isNull();
        assertThat(AgendaMedicoService.numeroWhatsapp(null)).isNull();
    }

    @Test
    void eliminarCancelaLaCitaLiberaElTurnoYDevuelveLosDatosParaElCorreo() {
        String medico = medicoConAgenda();
        String paciente = pacienteNuevo("999111222");
        ReservaCreada r = reservar(paciente, medico, "09:30");

        CitaCancelada c = agenda.eliminar(medico, r.id(), "Tuve una emergencia");

        assertThat(c.pacienteEmail()).isEqualTo(paciente);
        assertThat(c.motivo()).isEqualTo("Tuve una emergencia");
        Cita guardada = citaRepo.findById(r.id()).orElseThrow();
        assertThat(guardada.getEstado()).isEqualTo(EstadoCita.CANCELADA);
        assertThat(guardada.getMotivoCancelacion()).isEqualTo("Tuve una emergencia");

        // Ya cancelada, no se puede eliminar otra vez.
        assertThatThrownBy(() -> agenda.eliminar(medico, r.id(), null)).isInstanceOf(NegocioException.class);

        // El turno vuelve a estar libre para otro paciente.
        ReservaCreada otra = reservar(pacienteNuevo("999111333"), medico, "09:30");
        assertThat(otra.estado()).isEqualTo("Pendiente");
    }

    @Test
    void unMedicoNoPuedeEliminarLaCitaDeOtro() {
        String medico = medicoConAgenda();
        String otroMedico = medicoConAgenda();
        ReservaCreada r = reservar(pacienteNuevo("999111222"), medico, "10:00");

        assertThatThrownBy(() -> agenda.eliminar(otroMedico, r.id(), null)).isInstanceOf(NegocioException.class);
        assertThat(citaRepo.findById(r.id()).orElseThrow().getEstado()).isEqualTo(EstadoCita.PENDIENTE);
    }

    @Test
    void elCorreoDeReservaYElDeEliminacionLlegaAlPaciente() {
        String medico = medicoConAgenda();
        String paciente = pacienteNuevo("999111222");
        ReservaCreada r = reservar(paciente, medico, "10:30");

        assertThat(notificaciones.reservaCreada(paciente, "Ana Paciente", r)).isTrue();
        verify(correo).enviarHtml(org.mockito.ArgumentMatchers.eq(paciente), contains("reservada"), contains(r.codigo()));

        CitaCancelada c = agenda.eliminar(medico, r.id(), "Imprevisto");
        assertThat(notificaciones.citaEliminada(c)).isTrue();
        verify(correo).enviarHtml(org.mockito.ArgumentMatchers.eq(paciente), contains("cancelada"), contains("Imprevisto"));
    }

    @Test
    void siElCorreoFallaLaOperacionSigueVigenteYSeIndica() {
        String medico = medicoConAgenda();
        String paciente = pacienteNuevo("999111222");
        ReservaCreada r = reservar(paciente, medico, "11:00");
        org.mockito.Mockito.doThrow(new NegocioException("sin correo")).when(correo)
                .enviarHtml(anyString(), anyString(), anyString());

        assertThat(notificaciones.reservaCreada(paciente, "Ana Paciente", r)).isFalse();
        assertThat(citaRepo.findById(r.id()).orElseThrow().getEstado()).isEqualTo(EstadoCita.PENDIENTE);
        verify(correo, never()).enviarTexto(anyString(), anyString(), anyString());
    }
}
