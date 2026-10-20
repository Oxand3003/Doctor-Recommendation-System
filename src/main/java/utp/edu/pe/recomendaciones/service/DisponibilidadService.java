package utp.edu.pe.recomendaciones.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.dto.DisponibilidadView;
import utp.edu.pe.recomendaciones.dto.DisponibilidadView.DiaView;
import utp.edu.pe.recomendaciones.dto.DisponibilidadView.FranjaView;
import utp.edu.pe.recomendaciones.dto.DisponibilidadView.ModalidadOpcion;
import utp.edu.pe.recomendaciones.dto.DisponibilidadView.ReservaView;
import utp.edu.pe.recomendaciones.dto.HorarioForm;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Cita;
import utp.edu.pe.recomendaciones.model.HorarioAtencion;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.enums.EstadoCita;
import utp.edu.pe.recomendaciones.model.enums.Modalidad;
import utp.edu.pe.recomendaciones.model.enums.ModalidadAtencion;
import utp.edu.pe.recomendaciones.repository.AgendaCitaRepository;
import utp.edu.pe.recomendaciones.repository.HorarioAtencionRepository;

/**
 * Días y horarios de atención del médico (HorarioAtencion). DoctorProfileService ya genera los turnos
 * a partir de estas filas, así que lo que se guarda aquí se refleja solo en el perfil público y en la
 * agenda de reservas. Reglas: horaFin > horaInicio, duración positiva que quepa en la franja y sin
 * solapes entre franjas ACTIVAS del mismo día (las desactivadas pueden solaparse, pero no se pueden
 * reactivar si chocan con una activa).
 */
@Service
public class DisponibilidadService {

    public static final int MAX_FRANJAS = 70;
    private static final int DURACION_MIN = 5;
    private static final int DURACION_MAX = 240;
    private static final String[] DIAS = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("EEE d MMM",
            Locale.forLanguageTag("es-PE"));
    /** Cuántos días hacia adelante se listan los turnos ya reservados. */
    static final int DIAS_RESERVAS = 14;

    private final MedicoSesion sesion;
    private final HorarioAtencionRepository horarios;
    private final AgendaCitaRepository citas;

    public DisponibilidadService(MedicoSesion sesion, HorarioAtencionRepository horarios,
                                 AgendaCitaRepository citas) {
        this.sesion = sesion;
        this.horarios = horarios;
        this.citas = citas;
    }

    // ------------------------------------------------------------------
    // LECTURA
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public DisponibilidadView ver(String email) {
        Medico m = sesion.actual(email);
        List<HorarioAtencion> todas = horarios.findByMedicoIdOrderByDiaSemanaAscHoraInicioAsc(m.getId());

        Map<Integer, List<FranjaView>> porDia = new TreeMap<>();
        for (int d = 1; d <= 7; d++) porDia.put(d, new ArrayList<>());
        for (HorarioAtencion h : todas) {
            int dia = h.getDiaSemana();
            if (dia < 1 || dia > 7) continue;
            long minutos = Duration.between(h.getHoraInicio(), h.getHoraFin()).toMinutes();
            int turnos = h.getDuracionCitaMin() > 0 ? (int) (minutos / h.getDuracionCitaMin()) : 0;
            porDia.get(dia).add(new FranjaView(h.getId(), dia, h.getHoraInicio().format(HORA),
                    h.getHoraFin().format(HORA), h.getDuracionCitaMin(), h.getModalidad().name(),
                    etiqueta(h.getModalidad()), h.isActivo(), turnos));
        }
        List<DiaView> dias = porDia.entrySet().stream()
                .map(e -> new DiaView(e.getKey(), DIAS[e.getKey() - 1], e.getValue())).toList();
        List<ModalidadOpcion> modalidades = Arrays.stream(ModalidadAtencion.values())
                .map(x -> new ModalidadOpcion(x.name(), etiqueta(x))).toList();
        int activas = (int) todas.stream().filter(HorarioAtencion::isActivo).count();
        return new DisponibilidadView(dias, modalidades, activas, todas.size(), MAX_FRANJAS, reservas(m));
    }

    /**
     * Turnos que los pacientes ya reservaron (PENDIENTE o CONFIRMADA) en los próximos días. Salen de la misma
     * tabla de citas que usan la agenda y los turnos del perfil público, así que lo que se reserva aparece
     * aquí al instante y esos horarios ya no se ofrecen a otros pacientes.
     */
    private List<ReservaView> reservas(Medico m) {
        LocalDate hoy = LocalDate.now();
        return citas.entre(m.getId(), hoy.atStartOfDay(), hoy.plusDays(DIAS_RESERVAS + 1L).atStartOfDay()).stream()
                .filter(c -> c.getEstado() == EstadoCita.PENDIENTE || c.getEstado() == EstadoCita.CONFIRMADA)
                .map(this::reserva)
                .toList();
    }

    private ReservaView reserva(Cita c) {
        String fecha = c.getFechaHoraInicio().format(DIA);
        return new ReservaView(c.getId(), fecha.substring(0, 1).toUpperCase() + fecha.substring(1),
                c.getFechaHoraInicio().format(HORA) + "–" + c.getFechaHoraFin().format(HORA),
                (c.getPaciente().getNombres() + " " + c.getPaciente().getApellidos()).trim(),
                c.getModalidad() == Modalidad.TELECONSULTA ? "Teleconsulta" : "Presencial",
                c.getEstado() == EstadoCita.PENDIENTE ? "Pendiente" : "Confirmada",
                c.getEstado().name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // ESCRITURA
    // ------------------------------------------------------------------
    /** Crea una franja por cada día marcado. Todo o nada: si alguna falla no se guarda ninguna. */
    @Transactional
    public int crear(String email, HorarioForm f) {
        Medico m = sesion.actual(email);
        Datos d = validar(f);
        List<Integer> dias = diasElegidos(f);
        if (horarios.countByMedicoId(m.getId()) + dias.size() > MAX_FRANJAS) {
            throw new NegocioException("Puedes tener hasta " + MAX_FRANJAS + " franjas de atención.");
        }
        for (int dia : dias) {
            if (f.activo()) comprobarSolape(m.getId(), dia, d.inicio, d.fin, null);
        }
        for (int dia : dias) {
            HorarioAtencion h = new HorarioAtencion();
            h.setMedico(m);
            aplicar(h, dia, d, f.activo());
            horarios.save(h);
        }
        return dias.size();
    }

    @Transactional
    public void actualizar(String email, Long id, HorarioForm f) {
        Medico m = sesion.actual(email);
        HorarioAtencion h = horarios.findByIdAndMedicoId(id, m.getId())
                .orElseThrow(() -> new NegocioException("La franja indicada no existe."));
        Datos d = validar(f);
        List<Integer> dias = diasElegidos(f);
        if (dias.size() != 1) {
            throw new NegocioException("Al editar una franja elige un solo día.");
        }
        int dia = dias.get(0);
        if (f.activo()) comprobarSolape(m.getId(), dia, d.inicio, d.fin, h.getId());
        aplicar(h, dia, d, f.activo());
        horarios.save(h);
    }

    @Transactional
    public void eliminar(String email, Long id) {
        Medico m = sesion.actual(email);
        HorarioAtencion h = horarios.findByIdAndMedicoId(id, m.getId())
                .orElseThrow(() -> new NegocioException("La franja indicada no existe."));
        horarios.delete(h);
    }

    /** Activa o desactiva una franja sin borrarla. Devuelve el nuevo estado (true = activa). */
    @Transactional
    public boolean alternar(String email, Long id) {
        Medico m = sesion.actual(email);
        HorarioAtencion h = horarios.findByIdAndMedicoId(id, m.getId())
                .orElseThrow(() -> new NegocioException("La franja indicada no existe."));
        boolean nuevo = !h.isActivo();
        if (nuevo) comprobarSolape(m.getId(), h.getDiaSemana(), h.getHoraInicio(), h.getHoraFin(), h.getId());
        h.setActivo(nuevo);
        horarios.save(h);
        return nuevo;
    }

    // ------------------------------------------------------------------
    // Validación
    // ------------------------------------------------------------------
    private record Datos(LocalTime inicio, LocalTime fin, int duracion, ModalidadAtencion modalidad) { }

    private Datos validar(HorarioForm f) {
        LocalTime inicio = hora(f.horaInicio(), "La hora de inicio");
        LocalTime fin = hora(f.horaFin(), "La hora de fin");
        if (!fin.isAfter(inicio)) {
            throw new NegocioException("La hora de fin debe ser posterior a la hora de inicio.");
        }
        Integer dur = f.duracionMin();
        if (dur == null || dur < DURACION_MIN || dur > DURACION_MAX) {
            throw new NegocioException("La duración de cada consulta debe estar entre " + DURACION_MIN
                    + " y " + DURACION_MAX + " minutos.");
        }
        if (Duration.between(inicio, fin).toMinutes() < dur) {
            throw new NegocioException("La duración de la consulta (" + dur + " min) es mayor que la franja.");
        }
        ModalidadAtencion mod;
        try {
            mod = ModalidadAtencion.valueOf(f.modalidad() == null ? "" : f.modalidad().trim());
        } catch (IllegalArgumentException e) {
            throw new NegocioException("Selecciona una modalidad de atención válida.");
        }
        return new Datos(inicio, fin, dur, mod);
    }

    private LocalTime hora(String raw, String campo) {
        try {
            return LocalTime.parse(raw == null ? "" : raw.trim());
        } catch (DateTimeParseException e) {
            throw new NegocioException(campo + " no es válida (usa el formato HH:mm).");
        }
    }

    private List<Integer> diasElegidos(HorarioForm f) {
        List<Integer> dias = f.dias() == null ? List.of()
                : f.dias().stream().filter(x -> x != null).distinct().sorted().toList();
        if (dias.isEmpty()) throw new NegocioException("Elige al menos un día de la semana.");
        if (dias.stream().anyMatch(x -> x < 1 || x > 7)) {
            throw new NegocioException("Hay un día de la semana que no es válido.");
        }
        return dias;
    }

    /** Dos franjas del mismo día se solapan si cada una empieza antes de que termine la otra. */
    private void comprobarSolape(Long medicoId, int dia, LocalTime inicio, LocalTime fin, Long excluirId) {
        for (HorarioAtencion o : horarios.findByMedicoIdAndDiaSemana(medicoId, dia)) {
            if (!o.isActivo() || o.getId().equals(excluirId)) continue;
            if (inicio.isBefore(o.getHoraFin()) && o.getHoraInicio().isBefore(fin)) {
                throw new NegocioException("La franja se solapa con " + o.getHoraInicio().format(HORA) + " – "
                        + o.getHoraFin().format(HORA) + " del " + DIAS[dia - 1].toLowerCase() + ".");
            }
        }
    }

    private void aplicar(HorarioAtencion h, int dia, Datos d, boolean activo) {
        h.setDiaSemana(dia);
        h.setHoraInicio(d.inicio);
        h.setHoraFin(d.fin);
        h.setDuracionCitaMin(d.duracion);
        h.setModalidad(d.modalidad);
        h.setActivo(activo);
    }

    static String etiqueta(ModalidadAtencion m) {
        return switch (m) {
            case PRESENCIAL -> "Presencial";
            case TELECONSULTA -> "Teleconsulta";
            case AMBAS -> "Presencial y teleconsulta";
        };
    }
}
