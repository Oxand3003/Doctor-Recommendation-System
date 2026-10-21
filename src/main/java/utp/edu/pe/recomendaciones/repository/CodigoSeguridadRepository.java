package utp.edu.pe.recomendaciones.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import utp.edu.pe.recomendaciones.model.CodigoSeguridad;
import utp.edu.pe.recomendaciones.model.enums.PropositoCodigo;

public interface CodigoSeguridadRepository extends JpaRepository<CodigoSeguridad, Long> {

    /**
     * Códigos sin consumir de un usuario para un propósito, con la fila bloqueada: dos peticiones simultáneas
     * (p. ej. dos intentos a la vez) se ejecutan una detrás de otra y el contador de intentos no se pisa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<CodigoSeguridad> findByUsuarioIdAndPropositoAndConsumidoFalse(Long usuarioId, PropositoCodigo proposito);

    /** Solo lectura (sin bloqueo) para dibujar el perfil: ¿hay un código pendiente? */
    Optional<CodigoSeguridad> findFirstByUsuarioIdAndPropositoAndConsumidoFalseAndExpiraEnAfterOrderByCreadoEnDesc(
            Long usuarioId, PropositoCodigo proposito, LocalDateTime ahora);

    /** Último código emitido (consumido o no): sirve para el tiempo mínimo entre reenvíos. */
    Optional<CodigoSeguridad> findFirstByUsuarioIdAndPropositoOrderByCreadoEnDesc(Long usuarioId, PropositoCodigo proposito);

    /** Cuántos códigos se emitieron desde cierto momento (tope por hora, contando ambos propósitos). */
    long countByUsuarioIdAndCreadoEnAfter(Long usuarioId, LocalDateTime desde);

    /** Limpieza: borra los códigos ya vencidos o consumidos de este usuario. */
    void deleteByUsuarioIdAndExpiraEnBefore(Long usuarioId, LocalDateTime limite);
}
