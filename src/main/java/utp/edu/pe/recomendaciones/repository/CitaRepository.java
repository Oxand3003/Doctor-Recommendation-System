package utp.edu.pe.recomendaciones.repository;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import utp.edu.pe.recomendaciones.model.Cita;
import utp.edu.pe.recomendaciones.model.enums.EstadoCita;

public interface CitaRepository extends JpaRepository<Cita, Long> {

    /** La comunicación de la pareja está habilitada mientras exista al menos una cita PENDIENTE. */
    boolean existsByPaciente_IdAndMedico_IdAndEstado(Long pacienteId, Long medicoId, EstadoCita estado);

    List<Cita> findByPacienteIdOrderByFechaHoraInicioDesc(Long pacienteId);

    List<Cita> findByEstadoInAndFechaHoraFinBefore(List<EstadoCita> estados, LocalDateTime limite);

    List<Cita> findByPacienteIdAndMedicoIdAndEstadoOrderByFechaHoraFinDesc(Long pacienteId, Long medicoId,
                                                                             EstadoCita estado);

    List<Cita> findByMedicoIdAndFechaHoraInicioBetween(Long medicoId, LocalDateTime desde, LocalDateTime hasta);

    boolean existsByMedicoIdAndFechaHoraInicio(Long medicoId, LocalDateTime fechaHoraInicio);

    /** Cita (de cualquier estado) que ocupa exactamente ese inicio; sirve para reutilizar una CANCELADA. */
    Optional<Cita> findByMedicoIdAndFechaHoraInicio(Long medicoId, LocalDateTime fechaHoraInicio);

    /**
     * Citas del médico, NO canceladas, que se cruzan con [inicio, fin). Una cita cancelada libera su turno;
     * cualquier otra (pendiente, confirmada, completada, no asistió) lo mantiene ocupado, igual que
     * DoctorProfileService al generar los turnos.
     */
    @Query("select count(c) from Cita c where c.medico.id = :medicoId and c.estado <> :cancelada "
            + "and c.fechaHoraInicio < :fin and c.fechaHoraFin > :inicio")
    long contarCruzadasDelMedico(@Param("medicoId") Long medicoId, @Param("inicio") LocalDateTime inicio,
                                 @Param("fin") LocalDateTime fin, @Param("cancelada") EstadoCita cancelada);

    /** Citas del paciente, NO canceladas, que se cruzan con [inicio, fin): nadie está en dos citas a la vez. */
    @Query("select count(c) from Cita c where c.paciente.id = :pacienteId and c.estado <> :cancelada "
            + "and c.fechaHoraInicio < :fin and c.fechaHoraFin > :inicio")
    long contarCruzadasDelPaciente(@Param("pacienteId") Long pacienteId, @Param("inicio") LocalDateTime inicio,
                                   @Param("fin") LocalDateTime fin, @Param("cancelada") EstadoCita cancelada);

    /** Todas las citas del paciente con médico, especialidad y sede ya cargados (para "Mis citas"). */
    @Query("select c from Cita c join fetch c.medico m join fetch m.usuario join fetch m.especialidad "
            + "left join fetch m.establecimiento where c.paciente.id = :pacienteId "
            + "order by c.fechaHoraInicio desc")
    List<Cita> delPacienteConMedico(@Param("pacienteId") Long pacienteId);

    //  pacientes atendidos
    long countByMedicoIdAndEstado(Long medicoId, EstadoCita estado);
}
