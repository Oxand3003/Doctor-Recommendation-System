package utp.edu.pe.recomendaciones.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Cita;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.Valoracion;
import utp.edu.pe.recomendaciones.model.enums.EstadoCita;
import utp.edu.pe.recomendaciones.repository.CitaRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;
import utp.edu.pe.recomendaciones.repository.ValoracionRepository;

/** Guarda comentarios de pacientes únicamente sobre sus citas ya atendidas. */
@Service
public class ValoracionPacienteService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final UsuarioRepository usuarios;
    private final CitaRepository citas;
    private final ValoracionRepository valoraciones;
    private final CitaEstadoService estados;

    public ValoracionPacienteService(UsuarioRepository usuarios, CitaRepository citas,
                                    ValoracionRepository valoraciones, CitaEstadoService estados) {
        this.usuarios = usuarios;
        this.citas = citas;
        this.valoraciones = valoraciones;
        this.estados = estados;
    }

    @Transactional
    public void guardar(String email, Long citaId, int puntaje, String comentario) {
        Usuario paciente = usuarios.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new NegocioException("Cuenta de paciente no encontrada."));
        Cita cita = citas.findById(citaId)
                .orElseThrow(() -> new NegocioException("La cita indicada no existe."));
        if (!cita.getPaciente().getId().equals(paciente.getId())) {
            throw new NegocioException("No puedes comentar una cita de otro paciente.");
        }
        if (cita.getFechaHoraFin().isAfter(LocalDateTime.now(LIMA))) {
            throw new NegocioException("Solo puedes comentar una cita que ya terminó.");
        }
        if (cita.getEstado() != EstadoCita.COMPLETADA) {
            throw new NegocioException("La cita todavía no está marcada como atendida.");
        }
        if (valoraciones.existsByCitaId(citaId)) {
            throw new NegocioException("Ya dejaste una valoración para esta cita.");
        }
        if (puntaje < 1 || puntaje > 5) {
            throw new NegocioException("La valoración debe estar entre 1 y 5 estrellas.");
        }
        String texto = comentario == null ? "" : comentario.trim();
        if (texto.isBlank() || texto.length() > 500) {
            throw new NegocioException("Escribe un comentario de entre 1 y 500 caracteres.");
        }

        Medico medico = cita.getMedico();
        Valoracion valoracion = new Valoracion();
        valoracion.setCita(cita);
        valoracion.setPaciente(paciente);
        valoracion.setMedico(medico);
        valoracion.setPuntajeGeneral(puntaje);
        valoracion.setComentario(texto);
        valoraciones.save(valoracion);

        List<Valoracion> todas = valoraciones.findByMedicoIdOrderByCreadaEnDesc(medico.getId());
        medico.setTotalValoraciones(todas.size());
        medico.setRatingPromedio(todas.stream().mapToInt(Valoracion::getPuntajeGeneral).average().orElse(0));
    }

    @Transactional(readOnly = true)
    public Optional<Long> citaParaValorar(String email, Long medicoId) {
        estados.marcarAtendidas();
        Usuario paciente = usuarios.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new NegocioException("Cuenta de paciente no encontrada."));
        return citas.findByPacienteIdAndMedicoIdAndEstadoOrderByFechaHoraFinDesc(
                        paciente.getId(), medicoId, EstadoCita.COMPLETADA).stream()
                .filter(c -> !c.getFechaHoraFin().isAfter(LocalDateTime.now(LIMA)))
                .filter(c -> !valoraciones.existsByCitaId(c.getId()))
                .findFirst()
                .map(Cita::getId);
    }

    @Transactional
    public void eliminar(String email, Long valoracionId) {
        Usuario paciente = usuarios.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new NegocioException("Cuenta de paciente no encontrada."));
        Valoracion valoracion = valoraciones.findById(valoracionId)
                .orElseThrow(() -> new NegocioException("El comentario indicado no existe."));
        if (!valoracion.getPaciente().getId().equals(paciente.getId())) {
            throw new NegocioException("No puedes eliminar el comentario de otro paciente.");
        }
        Medico medico = valoracion.getMedico();
        valoraciones.delete(valoracion);
        List<Valoracion> restantes = valoraciones.findByMedicoIdOrderByCreadaEnDesc(medico.getId());
        medico.setTotalValoraciones(restantes.size());
        medico.setRatingPromedio(restantes.stream().mapToInt(Valoracion::getPuntajeGeneral).average().orElse(0));
    }
}
