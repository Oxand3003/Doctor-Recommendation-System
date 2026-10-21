package utp.edu.pe.recomendaciones.Controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import utp.edu.pe.recomendaciones.dto.FormacionForm;
import utp.edu.pe.recomendaciones.dto.PerfilMedicoForm;
import utp.edu.pe.recomendaciones.dto.PerfilPrivadoView;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.service.CredencialesMedicoService;
import utp.edu.pe.recomendaciones.service.PerfilMedicoService;

/**
 * Perfil PRIVADO del médico con sesión iniciada. Requiere autenticación (anyRequest en SecurityConfig)
 * y el servicio exige además rol MEDICO y cuenta ACTIVA; el médico se toma de la sesión, nunca de la URL.
 * Los formularios hacen POST real (con CSRF automático de th:action) y vuelven con Post/Redirect/Get.
 */
@Controller
@RequestMapping("/medico/perfil")
public class MedicoPerfilController {

    private static final String DESTINO = "redirect:/medico/perfil";

    private final PerfilMedicoService perfiles;
    private final CredencialesMedicoService credenciales;

    public MedicoPerfilController(PerfilMedicoService perfiles, CredencialesMedicoService credenciales) {
        this.perfiles = perfiles;
        this.credenciales = credenciales;
    }

    @GetMapping
    public String ver(Authentication auth, Model model) {
        if (!esMedico(auth)) {
            return "redirect:/account";
        }
        PerfilPrivadoView vista = perfiles.perfil(auth.getName());
        // Tras un error de validación se vuelven a mostrar los datos que el médico escribió.
        if (model.asMap().get("form") instanceof PerfilMedicoForm escrito) {
            vista = vista.conDatos(escrito);
        }
        model.addAttribute("p", vista);
        // Sección "Seguridad de la cuenta": solo indica si hay un código pendiente (nunca el código).
        model.addAttribute("cred", credenciales.estado(auth.getName()));
        return "medico/perfil";
    }

    // ---------------- Información profesional ----------------
    @PostMapping
    public String guardar(@RequestParam Map<String, String> p, Authentication auth, RedirectAttributes ra) {
        PerfilMedicoForm f = new PerfilMedicoForm(
                p.get("nombres"), p.get("apellidos"), largo(p.get("especialidadId")),
                largo(p.get("establecimientoId")), p.get("tituloProfesional"), p.get("universidad"),
                entero(p.get("anioEgreso")), entero(p.get("aniosExperiencia")), p.get("biografia"),
                p.get("mensajePredeterminadoChat"));
        try {
            perfiles.actualizarPerfil(auth.getName(), f);
            ra.addFlashAttribute("ok", "Tu perfil se actualizó correctamente.");
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("form", f);
            ra.addFlashAttribute("reabrir", "editar");
        }
        return DESTINO;
    }

    // ---------------- Formación ----------------
    @PostMapping("/formacion")
    public String agregarFormacion(@RequestParam Map<String, String> p, Authentication auth, RedirectAttributes ra) {
        return ejecutar(ra, "Formación agregada.", () -> perfiles.agregarFormacion(auth.getName(), formacion(p)));
    }

    @PostMapping("/formacion/{id}")
    public String actualizarFormacion(@PathVariable Long id, @RequestParam Map<String, String> p,
                                      Authentication auth, RedirectAttributes ra) {
        return ejecutar(ra, "Formación actualizada.",
                () -> perfiles.actualizarFormacion(auth.getName(), id, formacion(p)));
    }

    @PostMapping("/formacion/{id}/eliminar")
    public String eliminarFormacion(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        return ejecutar(ra, "Formación eliminada.", () -> perfiles.eliminarFormacion(auth.getName(), id));
    }

    // ---------------- Fotos ----------------
    @PostMapping("/foto")
    public String cambiarFotoPerfil(@RequestParam(value = "foto", required = false) MultipartFile foto,
                                    Authentication auth, RedirectAttributes ra) {
        return ejecutar(ra, "Foto de perfil actualizada.", () -> perfiles.cambiarFotoPerfil(auth.getName(), foto));
    }

    @PostMapping("/foto/eliminar")
    public String eliminarFotoPerfil(Authentication auth, RedirectAttributes ra) {
        return ejecutar(ra, "Foto de perfil eliminada.", () -> perfiles.eliminarFotoPerfil(auth.getName()));
    }

    @PostMapping("/galeria")
    public String agregarGaleria(@RequestParam(value = "fotos", required = false) List<MultipartFile> fotos,
                                 Authentication auth, RedirectAttributes ra) {
        return ejecutar(ra, "Fotos agregadas a tu galería.", () -> perfiles.agregarFotosGaleria(auth.getName(), fotos));
    }

    @PostMapping("/galeria/{id}/eliminar")
    public String eliminarGaleria(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        return ejecutar(ra, "Foto eliminada.", () -> perfiles.eliminarFotoGaleria(auth.getName(), id));
    }

    // ---------------- Auxiliares ----------------
    /** Ejecuta la acción; éxito → mensaje "ok", error de negocio → mensaje "error". Siempre redirige al perfil. */
    private String ejecutar(RedirectAttributes ra, String mensajeOk, Runnable accion) {
        try {
            accion.run();
            ra.addFlashAttribute("ok", mensajeOk);
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return DESTINO;
    }

    private FormacionForm formacion(Map<String, String> p) {
        return new FormacionForm(p.get("tipo"), p.get("titulo"), p.get("institucion"), entero(p.get("anio")));
    }

    private boolean esMedico(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_MEDICO"::equals);
    }

    private Integer entero(String s) {
        try {
            return s == null || s.isBlank() ? null : Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long largo(String s) {
        try {
            return s == null || s.isBlank() ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
