package utp.edu.pe.recomendaciones.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import utp.edu.pe.recomendaciones.model.MensajeChat;

public interface MensajeChatRepository extends JpaRepository<MensajeChat, Long> {
    List<MensajeChat> findByConversacion_IdOrderByCreadoEnAsc(Long conversacionId);
    Optional<MensajeChat> findFirstByConversacion_IdOrderByCreadoEnDesc(Long conversacionId);
    Optional<MensajeChat> findByConversacion_IdAndClientId(Long conversacionId, String clientId);

    @Query("select count(m) from MensajeChat m where m.conversacion.id = :conversacionId "
            + "and m.emisor.id <> :usuarioId and m.leidoEn is null")
    long contarNoLeidos(@Param("conversacionId") Long conversacionId, @Param("usuarioId") Long usuarioId);

    @Query("select m from MensajeChat m where m.conversacion.id = :conversacionId "
            + "and m.emisor.id <> :usuarioId and m.leidoEn is null order by m.creadoEn")
    List<MensajeChat> pendientesDeLectura(@Param("conversacionId") Long conversacionId,
                                          @Param("usuarioId") Long usuarioId);
}
