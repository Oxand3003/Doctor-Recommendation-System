package utp.edu.pe.recomendaciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import utp.edu.pe.recomendaciones.dto.AccountView;
import utp.edu.pe.recomendaciones.dto.AgendaView;
import utp.edu.pe.recomendaciones.dto.PerfilReserva;
import utp.edu.pe.recomendaciones.dto.RegistroPacienteForm;
import utp.edu.pe.recomendaciones.dto.ReservaCreada;
import utp.edu.pe.recomendaciones.dto.SlotView;
import utp.edu.pe.recomendaciones.exception.ConflictoReservaException;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Cita;
import utp.edu.pe.recomendaciones.model.HorarioAtencion;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.enums.EstadoCita;
import utp.edu.pe.recomendaciones.model.enums.Modalidad;
import utp.edu.pe.recomendaciones.model.enums.ModalidadAtencion;
import utp.edu.pe.recomendaciones.repository.CitaRepository;
import utp.edu.pe.recomendaciones.repository.HorarioAtencionRepository;

/**
 * Reserva de citas del paciente: la cita nace PENDIENTE, el turno queda ocupado para los demás y en la agenda
 * del médico, no hay duplicados (ni siquiera con dos reservas simultáneas) y "Mis citas" separa pendientes e
 * historial. Cada prueba crea su propio médico (agenda todos los días 09:00-12:00, turnos de 30 min) y sus
 * propios pacientes, así que no dependen unas de otras.
 */
class CitaPacienteServiceTest extends BaseMedicosTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired CitaPacienteService reservas;
    @Autowired CuentaService cuentas;
    @Autowired DoctorProfileService perfiles;
    @Autowired AgendaMedicoService agenda;
    @Autowired DisponibilidadService disponibilidad;
    @Autowired CitaRepository citaRepo;
    @Autowired HorarioAtencionRepository horarioRepo;

    private final LocalDate manana = LocalDate.now(LIMA).plusDays(1);

    private record MedicoConAgenda(String email, Long id) { }

    // ------------------------------------------------------------------
    // Datos de prueba
    // ------------------------------------------------------------------
    private String pacienteNuevo() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String email = "paciente" + r.nextInt(1_000_000_000) + "@prueba.local";
        registro.registrarPaciente(new RegistroPacienteForm("Ana", "Paciente",
                String.valueOf(r.nextInt(10_000_000, 100_000_000)), "999111222", email, PASSWORD_INICIAL));
        return email;
    }

    private MedicoConAgenda medicoConAgenda() {
        String email = medicoActivo();
        Long id = new TransactionTemplate(transacciones).execute(status -> {
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
            return m.getId();
        });
        return new MedicoConAgenda(email, id);
    }

    private ReservaCreada reservar(String paciente, MedicoConAgenda m, String hora) {
        return reservas.reservar(paciente, String.valueOf(m.id()), manana.toString(), hora, "PRESENCIAL", "Chequeo");
    }

    private boolean libre(MedicoConAgenda m, String hora) {
        List<SlotView> slots = perfiles.getSlots(m.id(), manana, Modalidad.PRESENCIAL);
        return slots.stream().filter(s -> s.hora().equals(hora)).findFirst().orElseThrow().disponible();
    }

    // ------------------------------------------------------------------
    // Reservar: estado, disponibilidad y sincronización con el médico
    // ------------------------------------------------------------------
    @Test
    void reservarCreaUnaCitaPendienteYOcupaElTurno() {
        MedicoConAgenda m = medicoConAgenda();
        String paciente = pacienteNuevo();
        assertThat(libre(m, "09:30")).isTrue();

        ReservaCreada r = reservar(paciente, m, "09:30");

        assertThat(r.estado()).isEqualTo("Pendiente");
        assertThat(r.codigo()).startsWith("MED-");
        Cita c = citaRepo.findById(r.id()).orElseThrow();
        assertThat(c.getEstado()).isEqualTo(EstadoCita.PENDIENTE);
        assertThat(c.getFechaHoraInicio()).isEqualTo(manana.atTime(9, 30));
        assertThat(c.getFechaHoraFin()).isEqualTo(manana.atTime(10, 0));
        assertThat(c.getModalidad()).isEqualTo(Modalidad.PRESENCIAL);

        // El turno ya no se ofrece (en ninguna modalidad) y los vecinos siguen libres.
        assertThat(libre(m, "09:30")).isFalse();
        assertThat(perfiles.getSlots(m.id(), manana, Modalidad.TELECONSULTA).stream()
                .filter(s -> s.hora().equals("09:30")).findFirst().orElseThrow().disponible()).isFalse();
        assertThat(libre(m, "09:00")).isTrue();
        assertThat(libre(m, "10:00")).isTrue();
    }

    @Test
    void laCitaApareceEnLaAgendaYEnLosTurnosReservadosDelMedico() {
        MedicoConAgenda m = medicoConAgenda();
        reservar(pacienteNuevo(), m, "10:00");

        AgendaView a = agenda.agenda(m.email(), "dia", manana, false);
        assertThat(a.dias().get(0).citas()).hasSize(1);
        assertThat(a.dias().get(0).citas().get(0).estado()).isEqualTo("Pendiente");
        assertThat(a.dias().get(0).citas().get(0).hora()).isEqualTo("10:00");

        assertThat(agenda.dashboard(m.email()).citasPendientes()).isEqualTo(1);
        assertThat(disponibilidad.ver(m.email()).reservas()).hasSize(1);
        assertThat(disponibilidad.ver(m.email()).reservas().get(0).estado()).isEqualTo("Pendiente");
    }

    // ------------------------------------------------------------------
    // Sin duplicados
    // ------------------------------------------------------------------
    @Test
    void otroPacienteNoPuedeReservarUnTurnoOcupado() {
        MedicoConAgenda m = medicoConAgenda();
        reservar(pacienteNuevo(), m, "09:00");
        String otro = pacienteNuevo();

        assertThatThrownBy(() -> reservar(otro, m, "09:00")).isInstanceOf(ConflictoReservaException.class);
        assertThat(citaRepo.findByMedicoIdAndFechaHoraInicio(m.id(), manana.atTime(9, 0))).isPresent();
        assertThat(cuentas.cuenta(otro).pendientes()).isEmpty();
    }

    @Test
    void elMismoPacienteNoPuedeReservarDosVecesElMismoTurno() {
        MedicoConAgenda m = medicoConAgenda();
        String paciente = pacienteNuevo();
        reservar(paciente, m, "09:00");

        assertThatThrownBy(() -> reservar(paciente, m, "09:00")).isInstanceOf(ConflictoReservaException.class);
        assertThat(cuentas.cuenta(paciente).pendientes()).hasSize(1);
    }

    @Test
    void unPacienteNoPuedeTenerDosCitasALaMismaHora() {
        MedicoConAgenda a = medicoConAgenda();
        MedicoConAgenda b = medicoConAgenda();
        String paciente = pacienteNuevo();
        reservar(paciente, a, "11:00");

        assertThatThrownBy(() -> reservar(paciente, b, "11:00")).isInstanceOf(ConflictoReservaException.class);
        assertThat(libre(b, "11:00")).isTrue(); // el segundo médico no perdió su turno
    }

    @Test
    void dosPacientesQueReservanElMismoTurnoALaVezSoloUnoLoConsigue() throws Exception {
        MedicoConAgenda m = medicoConAgenda();
        List<String> pacientes = List.of(pacienteNuevo(), pacienteNuevo(), pacienteNuevo());
        ExecutorService pool = Executors.newFixedThreadPool(pacientes.size());
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (String p : pacientes) {
            resultados.add(pool.submit(() -> {
                salida.await();
                try {
                    reservar(p, m, "11:30");
                    return true;
                } catch (ConflictoReservaException e) {
                    return false;
                }
            }));
        }
        salida.countDown();
        long exitos = 0;
        for (Future<Boolean> f : resultados) if (f.get(60, TimeUnit.SECONDS)) exitos++;
        pool.shutdown();

        assertThat(exitos).isEqualTo(1);
        assertThat(citaRepo.findByMedicoIdAndFechaHoraInicio(m.id(), manana.atTime(11, 30))).isPresent();
        assertThat(libre(m, "11:30")).isFalse();
    }

    // ------------------------------------------------------------------
    // Turnos inválidos y permisos
    // ------------------------------------------------------------------
    @Test
    void rechazaTurnosQueNoExistenEnLaAgendaDelMedico() {
        MedicoConAgenda m = medicoConAgenda();
        String p = pacienteNuevo();
        String id = String.valueOf(m.id());

        // 09:10 no cae en la cuadrícula de 30 min; 13:00 está fuera de la franja; 11:45 no cabe entera.
        for (String hora : List.of("09:10", "13:00", "08:30", "11:45")) {
            assertThatThrownBy(() -> reservas.reservar(p, id, manana.toString(), hora, "PRESENCIAL", null))
                    .as("hora %s", hora).isInstanceOf(NegocioException.class)
                    .isNotInstanceOf(ConflictoReservaException.class);
        }
        // Día pasado
        assertThatThrownBy(() -> reservas.reservar(p, id, LocalDate.now(LIMA).minusDays(1).toString(),
                "09:00", "PRESENCIAL", null)).isInstanceOf(NegocioException.class);
        // Datos mal formados
        assertThatThrownBy(() -> reservas.reservar(p, id, "no-es-fecha", "09:00", "PRESENCIAL", null))
                .isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> reservas.reservar(p, id, manana.toString(), "09:00", "OTRA", null))
                .isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> reservas.reservar(p, "999999999", manana.toString(), "09:00", "PRESENCIAL", null))
                .isInstanceOf(NegocioException.class);
        assertThat(cuentas.cuenta(p).pendientes()).isEmpty();
    }

    @Test
    void unaFranjaInactivaNoRecibeCitas() {
        MedicoConAgenda m = medicoConAgenda();
        new TransactionTemplate(transacciones).executeWithoutResult(st ->
                horarioRepo.findByMedicoIdAndActivoTrue(m.id()).forEach(h -> {
                    h.setActivo(false);
                    horarioRepo.save(h);
                }));

        assertThatThrownBy(() -> reservar(pacienteNuevo(), m, "09:00")).isInstanceOf(NegocioException.class);
    }

    @Test
    void soloLosPacientesPuedenReservar() {
        MedicoConAgenda m = medicoConAgenda();

        assertThatThrownBy(() -> reservar(m.email(), m, "09:00")).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> reservar(ADMIN, m, "09:00")).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> reservar("nadie@prueba.local", m, "09:00")).isInstanceOf(NegocioException.class);
        assertThat(libre(m, "09:00")).isTrue();
    }

    @Test
    void perfilReservaDistingueAnonimoPacienteYOtrosRoles() {
        MedicoConAgenda m = medicoConAgenda();
        String paciente = pacienteNuevo();

        assertThat(reservas.perfilReserva(null).estado()).isEqualTo("ANONIMO");
        assertThat(reservas.perfilReserva("anonymousUser").estado()).isEqualTo("ANONIMO");
        PerfilReserva p = reservas.perfilReserva(paciente);
        assertThat(p.paciente()).isTrue();
        assertThat(p.nombre()).isEqualTo("Ana Paciente");
        assertThat(p.dni()).hasSize(8);
        assertThat(reservas.perfilReserva(m.email()).estado()).isEqualTo("OTRO");
    }

    // ------------------------------------------------------------------
    // Turnos cancelados
    // ------------------------------------------------------------------
    @Test
    void unTurnoCanceladoVuelveAOfrecerseYSePuedeReservarDeNuevo() {
        MedicoConAgenda m = medicoConAgenda();
        ReservaCreada primera = reservar(pacienteNuevo(), m, "10:30");
        new TransactionTemplate(transacciones).executeWithoutResult(st -> {
            Cita c = citaRepo.findById(primera.id()).orElseThrow();
            c.setEstado(EstadoCita.CANCELADA);
            c.setMotivoCancelacion("Prueba");
            citaRepo.save(c);
        });
        assertThat(libre(m, "10:30")).isTrue();

        String otro = pacienteNuevo();
        ReservaCreada segunda = reservar(otro, m, "10:30");

        // La tabla solo admite una fila por (médico, inicio): se reutiliza la cancelada.
        assertThat(segunda.id()).isEqualTo(primera.id());
        Cita c = citaRepo.findById(segunda.id()).orElseThrow();
        assertThat(c.getEstado()).isEqualTo(EstadoCita.PENDIENTE);
        assertThat(c.getMotivoCancelacion()).isNull();
        assertThat(cuentas.cuenta(otro).pendientes()).hasSize(1);
        assertThat(libre(m, "10:30")).isFalse();
    }

    // ------------------------------------------------------------------
    // Mis citas: pendientes e historial
    // ------------------------------------------------------------------
    @Test
    void misCitasSeparaPendientesDeHistorial() {
        MedicoConAgenda m = medicoConAgenda();
        String paciente = pacienteNuevo();

        // Historial: una cita completada y otra cancelada, ambas en el pasado
        new TransactionTemplate(transacciones).executeWithoutResult(st -> {
            Medico medico = medicos.findById(m.id()).orElseThrow();
            var usuario = usuarios.findByEmail(paciente).orElseThrow();
            LocalDate hoy = LocalDate.now(LIMA);
            int[] atras = {3, 5};
            EstadoCita[] estados = {EstadoCita.COMPLETADA, EstadoCita.CANCELADA};
            for (int i = 0; i < atras.length; i++) {
                Cita c = new Cita();
                c.setPaciente(usuario);
                c.setMedico(medico);
                c.setFechaHoraInicio(hoy.minusDays(atras[i]).atTime(10, 0));
                c.setFechaHoraFin(hoy.minusDays(atras[i]).atTime(10, 30));
                c.setModalidad(Modalidad.PRESENCIAL);
                c.setEstado(estados[i]);
                citaRepo.save(c);
            }
        });
        // Pendientes: dos reservas futuras (la más próxima debe salir primero)
        reservas.reservar(paciente, String.valueOf(m.id()), manana.plusDays(1).toString(), "10:00", "TELECONSULTA", null);
        reservar(paciente, m, "09:00");

        AccountView v = cuentas.cuenta(paciente);

        assertThat(v.pendientes()).hasSize(2);
        assertThat(v.pendientes()).allSatisfy(c -> {
            assertThat(c.estado()).isEqualTo("Pendiente");
            assertThat(c.medico()).isNotBlank();
            assertThat(c.fecha()).isNotBlank();
            assertThat(c.hora()).contains("–");
        });
        assertThat(v.pendientes().get(0).hora()).startsWith("09:00");       // la de mañana, antes que la de pasado mañana
        assertThat(v.pendientes().get(1).modalidad()).isEqualTo("Teleconsulta");
        assertThat(v.historial()).extracting(AccountView.CitaItem::estado).containsExactly("Completada", "Cancelada");
    }
}
