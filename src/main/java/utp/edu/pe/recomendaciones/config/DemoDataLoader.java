package utp.edu.pe.recomendaciones.config;




/***********************************************DATOS DE PRUEBA************************************************/
/************************************************************************************************************** */


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.model.Cita;
import utp.edu.pe.recomendaciones.model.Distrito;
import utp.edu.pe.recomendaciones.model.Especialidad;
import utp.edu.pe.recomendaciones.model.Establecimiento;
import utp.edu.pe.recomendaciones.model.FormacionMedico;
import utp.edu.pe.recomendaciones.model.HorarioAtencion;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.Rol;
import utp.edu.pe.recomendaciones.model.ServicioMedico;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.Valoracion;
import utp.edu.pe.recomendaciones.model.enums.EstadoCita;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.model.enums.Modalidad;
import utp.edu.pe.recomendaciones.model.enums.ModalidadAtencion;
import utp.edu.pe.recomendaciones.model.enums.TipoFormacion;
import utp.edu.pe.recomendaciones.repository.CitaRepository;
import utp.edu.pe.recomendaciones.repository.DistritoRepository;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;
import utp.edu.pe.recomendaciones.repository.EstablecimientoRepository;
import utp.edu.pe.recomendaciones.repository.FormacionMedicoRepository;
import utp.edu.pe.recomendaciones.repository.HorarioAtencionRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.repository.RolRepository;
import utp.edu.pe.recomendaciones.repository.ServicioMedicoRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;
import utp.edu.pe.recomendaciones.repository.ValoracionRepository;

/**
 * Carga 3 médicos verificados con horarios, servicios, formación y reseñas.
 * Solo corre si no hay médicos. Desactivar con: medicerca.demo-data=false
 * Médicos:   password "Medico123!"   (rosa.medina@medicerca.local, luis.quispe@..., ana.torres@...)
 * Pacientes: password "Paciente123!"
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "medicerca.demo-data", havingValue = "true", matchIfMissing = true)
public class DemoDataLoader implements ApplicationRunner {

    private final RolRepository rolRepo;
    private final UsuarioRepository usuarioRepo;
    private final EspecialidadRepository especialidadRepo;
    private final DistritoRepository distritoRepo;
    private final EstablecimientoRepository establecimientoRepo;
    private final MedicoRepository medicoRepo;
    private final HorarioAtencionRepository horarioRepo;
    private final ServicioMedicoRepository servicioRepo;
    private final FormacionMedicoRepository formacionRepo;
    private final CitaRepository citaRepo;
    private final ValoracionRepository valoracionRepo;
    private final PasswordEncoder encoder;

    public DemoDataLoader(RolRepository rolRepo, UsuarioRepository usuarioRepo,
                          EspecialidadRepository especialidadRepo, DistritoRepository distritoRepo,
                          EstablecimientoRepository establecimientoRepo, MedicoRepository medicoRepo,
                          HorarioAtencionRepository horarioRepo, ServicioMedicoRepository servicioRepo,
                          FormacionMedicoRepository formacionRepo, CitaRepository citaRepo,
                          ValoracionRepository valoracionRepo, PasswordEncoder encoder) {
        this.rolRepo = rolRepo;
        this.usuarioRepo = usuarioRepo;
        this.especialidadRepo = especialidadRepo;
        this.distritoRepo = distritoRepo;
        this.establecimientoRepo = establecimientoRepo;
        this.medicoRepo = medicoRepo;
        this.horarioRepo = horarioRepo;
        this.servicioRepo = servicioRepo;
        this.formacionRepo = formacionRepo;
        this.citaRepo = citaRepo;
        this.valoracionRepo = valoracionRepo;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (medicoRepo.count() > 0) return;

        // ---------- Catálogos ----------
        Especialidad cardio = especialidad("Cardiología", "Corazón y sistema circulatorio");
        Especialidad pediatria = especialidad("Pediatría", "Salud de niños y adolescentes");
        Especialidad dermato = especialidad("Dermatología", "Piel, cabello y uñas");

        Distrito sanBorja = distrito("San Borja");
        Distrito miraflores = distrito("Miraflores");
        Distrito surco = distrito("Santiago de Surco");

        Establecimiento clinicaSanBorja = establecimiento("Clínica San Borja",
                "Av. Javier Prado Este 2450, Consultorio 402", "Frente al Museo de la Nación", sanBorja, -12.0897, -77.0003);
        Establecimiento centroMiraflores = establecimiento("Centro Médico Miraflores",
                "Av. Larco 1150, Piso 5", "Cerca al Parque Kennedy", miraflores, -12.1219, -77.0297);
        Establecimiento policlinicoSurco = establecimiento("Policlínico Surco",
                "Av. Primavera 890, Consultorio 12", "Altura del Jockey Plaza", surco, -12.0870, -76.9760);

        // ---------- Pacientes ----------
        Usuario carlos = usuario("Carlos", "Mendoza", "45871234", "carlos.mendoza@correo.com", "987111222", "PACIENTE", "Paciente123!");
        Usuario elena = usuario("Elena", "Ramos", "46992345", "elena.ramos@correo.com", "987333444", "PACIENTE", "Paciente123!");
        Usuario jorge = usuario("Jorge", "Paredes", "47813456", "jorge.paredes@correo.com", "987555666", "PACIENTE", "Paciente123!");
        Usuario maria = usuario("María", "Flores", "48724567", "maria.flores@correo.com", "987777888", "PACIENTE", "Paciente123!");

        // ---------- Dra. Rosa Medina (Cardiología) ----------
        Medico rosa = medico(
                usuario("Rosa", "Medina Cárdenas", "40123456", "rosa.medina@medicerca.local", "999000111", "MEDICO", "Medico123!"),
                "58210", "24119", 15, "Médico cirujano", "UNMSM", 2005,
                "Médica cirujana graduada de la Universidad Nacional Mayor de San Marcos (UNMSM) con especialización en "
                        + "Cardiología por el Instituto Nacional Cardiovascular (INCOR). Con más de 15 años de experiencia "
                        + "clínica, se dedica al diagnóstico y control de enfermedades cardiovasculares, prevención de "
                        + "infartos, control de hipertensión refractaria y seguimiento de insuficiencia cardíaca.",
                "120", cardio, clinicaSanBorja);
        formacion(rosa, TipoFormacion.TITULO, "Médico cirujano", "Universidad Nacional Mayor de San Marcos (UNMSM)", 2005);
        formacion(rosa, TipoFormacion.ESPECIALIDAD, "Especialista en Cardiología", "Instituto Nacional Cardiovascular (INCOR)", 2011);
        formacion(rosa, TipoFormacion.MEMBRESIA, "Miembro titular", "Sociedad Peruana de Cardiología (SOPECAR)", 2013);
        formacion(rosa, TipoFormacion.PASANTIA, "Pasantía en Ecocardiografía avanzada", "Hospital Clínic de Barcelona, España", 2016);
        servicio(rosa, "Consulta cardiológica integral", "Evaluación clínica detallada, examen físico y prescripción de tratamiento.", "120");
        servicio(rosa, "Electrocardiograma (ECG) con informe", "Registro de la actividad eléctrica del corazón e interpretación al instante.", "80");
        servicio(rosa, "Riesgo quirúrgico cardiovascular", "Evaluación preoperatoria completa con informe certificado.", "150");
        servicio(rosa, "Monitoreo MAPA / Holter de 24 horas", "Monitoreo ambulatorio continuo de presión arterial y ritmo cardíaco.", "220");
        horario(rosa, new int[]{1, 2, 3, 4, 5}, "09:00", "12:00", 40, ModalidadAtencion.PRESENCIAL);
        horario(rosa, new int[]{1, 2, 3, 4, 5}, "15:00", "18:00", 40, ModalidadAtencion.AMBAS);
        horario(rosa, new int[]{6}, "09:00", "13:00", 40, ModalidadAtencion.PRESENCIAL);

        resena(rosa, carlos, 5, "09:00", Modalidad.PRESENCIAL, 5, 5, 5, 5, 5,
                "Excelente profesional. Me explicó con gran detalle la causa de mis palpitaciones y me tranquilizó mucho. La clínica es muy limpia y el tiempo de espera fue mínimo.");
        resena(rosa, elena, 14, "10:20", Modalidad.PRESENCIAL, 5, 5, 5, 5, 5,
                "Llevé a mi madre para su control de presión alta. La doctora fue muy paciente y amable, y se tomó el tiempo de explicar todo.");
        resena(rosa, jorge, 27, "15:40", Modalidad.TELECONSULTA, 5, 5, 5, 4, null,
                "Hice la consulta virtual para una segunda opinión sobre mi riesgo quirúrgico. La puntualidad y claridad fueron excepcionales.");
        resena(rosa, maria, 40, "09:40", Modalidad.PRESENCIAL, 4, 5, 4, 4, 5,
                "Muy buena atención y diagnóstico claro. Solo tuve que esperar unos minutos más de lo previsto.");

        // ---------- Dr. Luis Quispe (Pediatría) ----------
        Medico luis = medico(
                usuario("Luis", "Quispe Huamán", "41234567", "luis.quispe@medicerca.local", "999000222", "MEDICO", "Medico123!"),
                "61455", "30876", 9, "Médico cirujano", "Universidad Peruana Cayetano Heredia", 2013,
                "Pediatra con enfoque en crecimiento y desarrollo, vacunación y manejo de enfermedades respiratorias frecuentes en la infancia.",
                "90", pediatria, centroMiraflores);
        formacion(luis, TipoFormacion.TITULO, "Médico cirujano", "Universidad Peruana Cayetano Heredia", 2013);
        formacion(luis, TipoFormacion.ESPECIALIDAD, "Especialista en Pediatría", "Instituto Nacional de Salud del Niño", 2018);
        servicio(luis, "Consulta pediátrica", "Control del niño sano y atención de enfermedades agudas.", "90");
        servicio(luis, "Control de crecimiento y desarrollo", "Evaluación nutricional y del desarrollo psicomotor.", "100");
        horario(luis, new int[]{1, 3, 5}, "08:00", "13:00", 30, ModalidadAtencion.AMBAS);
        horario(luis, new int[]{2, 4}, "16:00", "20:00", 30, ModalidadAtencion.PRESENCIAL);
        resena(luis, elena, 9, "08:30", Modalidad.PRESENCIAL, 5, 5, 5, 5, 5,
                "Mi hijo no se asusta con él. Muy paciente y explica todo con claridad a los padres.");
        resena(luis, carlos, 21, "16:30", Modalidad.PRESENCIAL, 4, 4, 5, 4, 4,
                "Buena atención y recomendaciones útiles. El consultorio es cómodo para los niños.");

        // ---------- Dra. Ana Torres (Dermatología) ----------
        Medico ana = medico(
                usuario("Ana", "Torres Vega", "42345678", "ana.torres@medicerca.local", "999000333", "MEDICO", "Medico123!"),
                "70233", "35412", 6, "Médico cirujano", "Universidad de San Martín de Porres", 2018,
                "Dermatóloga clínica especializada en acné, dermatitis y control de lunares. Atiende pacientes adultos y adolescentes.",
                "110", dermato, policlinicoSurco);
        formacion(ana, TipoFormacion.TITULO, "Médico cirujano", "Universidad de San Martín de Porres", 2018);
        formacion(ana, TipoFormacion.ESPECIALIDAD, "Especialista en Dermatología", "Hospital Nacional Guillermo Almenara", 2022);
        servicio(ana, "Consulta dermatológica", "Evaluación de la piel y plan de tratamiento personalizado.", "110");
        servicio(ana, "Dermatoscopía digital", "Revisión detallada de lunares y lesiones pigmentadas.", "140");
        horario(ana, new int[]{2, 4}, "10:00", "14:00", 40, ModalidadAtencion.AMBAS);
        horario(ana, new int[]{1, 3, 5}, "16:00", "19:00", 40, ModalidadAtencion.TELECONSULTA);
        resena(ana, maria, 12, "10:40", Modalidad.PRESENCIAL, 5, 5, 5, 5, 5,
                "Mi acné mejoró muchísimo con el tratamiento. Muy profesional y cercana.");
        resena(ana, jorge, 30, "16:40", Modalidad.TELECONSULTA, 4, 4, 5, 4, null,
                "La teleconsulta fue ordenada y puntual; me envió la receta electrónica enseguida.");

        // ---------- Recalcular rating ----------
        List.of(rosa, luis, ana).forEach(this::recalcularRating);
    }

    // ================== helpers ==================
    private Especialidad especialidad(String nombre, String desc) {
        return especialidadRepo.findByNombre(nombre).orElseGet(() -> {
            Especialidad e = new Especialidad();
            e.setNombre(nombre);
            e.setDescripcion(desc);
            e.setActiva(true);
            return especialidadRepo.save(e);
        });
    }

    private Distrito distrito(String nombre) {
        return distritoRepo.findByNombreAndProvincia(nombre, "Lima").orElseGet(() -> {
            Distrito d = new Distrito();
            d.setNombre(nombre);
            d.setProvincia("Lima");
            d.setDepartamento("Lima");
            return distritoRepo.save(d);
        });
    }

    private Establecimiento establecimiento(String nombre, String dir, String ref, Distrito d, Double lat, Double lng) {
        return establecimientoRepo.findAll().stream()
                .filter(e -> e.getNombre().equals(nombre)).findFirst()
                .orElseGet(() -> {
                    Establecimiento e = new Establecimiento();
                    e.setNombre(nombre);
                    e.setDireccion(dir);
                    e.setReferencia(ref);
                    e.setDistrito(d);
                    e.setLatitud(lat);
                    e.setLongitud(lng);
                    return establecimientoRepo.save(e);
                });
    }

    private Rol rol(String nombre) {
        return rolRepo.findByNombre(nombre).orElseGet(() -> {
            Rol r = new Rol();
            r.setNombre(nombre);
            return rolRepo.save(r);
        });
    }

    private Usuario usuario(String nombres, String apellidos, String dni, String email,
                            String tel, String rol, String password) {
        Usuario u = new Usuario();
        u.setNombres(nombres);
        u.setApellidos(apellidos);
        u.setDni(dni);
        u.setEmail(email.toLowerCase());
        u.setTelefono(tel);
        u.setPasswordHash(encoder.encode(password));
        u.setEstado(EstadoUsuario.ACTIVO);
        u.setRol(rol(rol));
        return usuarioRepo.save(u);
    }

    private Medico medico(Usuario u, String cmp, String rne, int anios, String titulo, String universidad,
                          int egreso, String bio, String costo, Especialidad esp, Establecimiento est) {
        Medico m = new Medico();
        m.setUsuario(u);
        m.setCmp(cmp);
        m.setRne(rne);
        m.setAniosExperiencia(anios);
        m.setTituloProfesional(titulo);
        m.setUniversidad(universidad);
        m.setAnioEgreso(egreso);
        m.setBiografia(bio);
        m.setCostoConsulta(new BigDecimal(costo));
        m.setRatingPromedio(0);
        m.setTotalValoraciones(0);
        m.setEspecialidad(esp);
        m.setEstablecimiento(est);
        return medicoRepo.save(m);
    }

    private void formacion(Medico m, TipoFormacion tipo, String titulo, String institucion, Integer anio) {
        FormacionMedico f = new FormacionMedico();
        f.setMedico(m);
        f.setTipo(tipo);
        f.setTitulo(titulo);
        f.setInstitucion(institucion);
        f.setAnio(anio);
        formacionRepo.save(f);
    }

    private void servicio(Medico m, String nombre, String desc, String precio) {
        ServicioMedico s = new ServicioMedico();
        s.setMedico(m);
        s.setNombre(nombre);
        s.setDescripcion(desc);
        s.setPrecio(new BigDecimal(precio));
        s.setActivo(true);
        servicioRepo.save(s);
    }

    private void horario(Medico m, int[] dias, String ini, String fin, int dur, ModalidadAtencion mod) {
        for (int d : dias) {
            HorarioAtencion h = new HorarioAtencion();
            h.setMedico(m);
            h.setDiaSemana(d);
            h.setHoraInicio(LocalTime.parse(ini));
            h.setHoraFin(LocalTime.parse(fin));
            h.setDuracionCitaMin(dur);
            h.setModalidad(mod);
            h.setActivo(true);
            horarioRepo.save(h);
        }
    }

    /** Crea una cita COMPLETADA en el pasado y su valoración. */
    private void resena(Medico m, Usuario paciente, int diasAtras, String hora, Modalidad mod,
                        int general, Integer calidez, Integer explicacion, Integer puntualidad,
                        Integer instalaciones, String comentario) {
        LocalDateTime inicio = LocalDate.now().minusDays(diasAtras).atTime(LocalTime.parse(hora));

        Cita c = new Cita();
        c.setPaciente(paciente);
        c.setMedico(m);
        c.setFechaHoraInicio(inicio);
        c.setFechaHoraFin(inicio.plusMinutes(40));
        c.setModalidad(mod);
        c.setMotivo("Consulta de control");
        c.setEstado(EstadoCita.COMPLETADA);
        c.setPrecio(m.getCostoConsulta());
        c = citaRepo.save(c);

        Valoracion v = new Valoracion();
        v.setCita(c);
        v.setPaciente(paciente);
        v.setMedico(m);
        v.setPuntajeGeneral(general);
        v.setCalidez(calidez);
        v.setExplicacion(explicacion);
        v.setPuntualidad(puntualidad);
        v.setInstalaciones(instalaciones);
        v.setComentario(comentario);
        valoracionRepo.save(v);
    }

    private void recalcularRating(Medico m) {
        List<Valoracion> vs = valoracionRepo.findByMedicoIdOrderByCreadaEnDesc(m.getId());
        double avg = vs.stream().mapToInt(Valoracion::getPuntajeGeneral).average().orElse(0);
        m.setRatingPromedio(Math.round(avg * 10.0) / 10.0);
        m.setTotalValoraciones(vs.size());
        medicoRepo.save(m);
    }
}
