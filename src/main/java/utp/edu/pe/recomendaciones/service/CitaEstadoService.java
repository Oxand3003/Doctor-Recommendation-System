package utp.edu.pe.recomendaciones.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import utp.edu.pe.recomendaciones.model.Cita;
import utp.edu.pe.recomendaciones.model.enums.EstadoCita;
import utp.edu.pe.recomendaciones.repository.CitaRepository;

/** Cambia a ATENDIDA (COMPLETADA en la base) toda cita que ya terminó. */
@Service
public class CitaEstadoService {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final CitaRepository citas;

    public CitaEstadoService(CitaRepository citas) {
        this.citas = citas;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarAtendidas() {
        LocalDateTime ahora = LocalDateTime.now(LIMA);
        List<Cita> vencidas = citas.findByEstadoInAndFechaHoraFinBefore(
                List.of(EstadoCita.PENDIENTE, EstadoCita.CONFIRMADA), ahora);
        for (Cita cita : vencidas) {
            cita.setEstado(EstadoCita.COMPLETADA);
        }
    }
}
