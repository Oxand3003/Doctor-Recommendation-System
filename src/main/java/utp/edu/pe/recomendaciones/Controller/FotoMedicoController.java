package utp.edu.pe.recomendaciones.Controller;

import java.time.Duration;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import utp.edu.pe.recomendaciones.service.PerfilMedicoService;
import utp.edu.pe.recomendaciones.service.PerfilMedicoService.FotoArchivo;

/** Sirve las fotos de los médicos. /doctor/** es público en SecurityConfig; solo salen fotos de médicos ACTIVOS. */
@Controller
@RequestMapping("/doctor/foto")
public class FotoMedicoController {

    private final PerfilMedicoService perfiles;

    public FotoMedicoController(PerfilMedicoService perfiles) {
        this.perfiles = perfiles;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> foto(@PathVariable Long id) {
        FotoArchivo f = perfiles.foto(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.tipoContenido()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(12)).cachePublic())
                .body(f.recurso());
    }
}
