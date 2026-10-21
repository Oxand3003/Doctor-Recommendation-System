package utp.edu.pe.recomendaciones.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import utp.edu.pe.recomendaciones.dto.HorarioForm;
import utp.edu.pe.recomendaciones.dto.SedeForm;
import utp.edu.pe.recomendaciones.dto.TarifasForm;
import utp.edu.pe.recomendaciones.dto.TarifasView;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.service.DisponibilidadService;
import utp.edu.pe.recomendaciones.service.ResenaService;
import utp.edu.pe.recomendaciones.service.SedeService;
import utp.edu.pe.recomendaciones.service.TarifaService;

/**
 * Gestión privada del médico: tarifas, horarios, sedes y reseñas. Igual que MedicoPerfilController:
 * requiere sesión (anyRequest en SecurityConfig), el servicio exige rol MEDICO y cuenta ACTIVA, el
 * médico sale SIEMPRE de la sesión y los ids de recursos hijos se buscan con findByIdAndMedicoId
 * (o findByIdAndPropietarioId en sedes). POST real con CSRF de th:action + Post/Redirect/Get.
 */
@Controller
@RequestMapping("/medico")
public class MedicoGestionController {

    private final TarifaService tarifas;
    private final DisponibilidadService horarios;
    private final SedeService sedes;
    private final ResenaService resenas;

    public MedicoGestionController(TarifaService tarifas, DisponibilidadService horarios,
                                   SedeService sedes, ResenaService resenas) {
        this.tarifas = tarifas;
        this.horarios = horarios;
        this.sedes = sedes;
        this.resenas = resenas;
    }

    // ================== TARIFAS ==================
    @GetMapping("/tarifas")
    public String verTarifas(Authentication auth, Model model) {
        if (!esMedico(auth)) return "redirect:/account";
        TarifasView vista = tarifas.ver(auth.getName());
        if (model.asMap().get("form") instanceof TarifasForm escrito) vista = vista.conDatos(escrito);
        model.addAttribute("t", vista);
        model.addAttribute("seccion", "tarifas");
        return "medico/tarifas";
    }

    @PostMapping("/tarifas")
    public String guardarTarifas(@RequestParam Map<String, String> p, Authentication auth, RedirectAttributes ra) {
        TarifasForm f = new TarifasForm(p.get("costoConsulta"), p.get("costoTeleconsulta"), p.get("costoPorHora"));
        try {
            tarifas.guardar(auth.getName(), f);
            ra.addFlashAttribute("ok", "Tus tarifas se guardaron correctamente.");
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("form", f);
        }
        return "redirect:/medico/tarifas";
    }

    // ================== HORARIOS ==================
    @GetMapping("/horarios")
    public String verHorarios(Authentication auth, Model model) {
        if (!esMedico(auth)) return "redirect:/account";
        model.addAttribute("d", horarios.ver(auth.getName()));
        model.addAttribute("seccion", "horarios");
        return "medico/horarios";
    }

    @PostMapping("/horarios")
    public String crearHorario(@RequestParam MultiValueMap<String, String> p, Authentication auth, RedirectAttributes ra) {
        HorarioForm f = horario(p);
        return ejecutar(ra, "/medico/horarios", () -> {
            int n = horarios.crear(auth.getName(), f);
            return n == 1 ? "Franja agregada." : n + " franjas agregadas.";
        });
    }

    @PostMapping("/horarios/{id}")
    public String actualizarHorario(@PathVariable Long id, @RequestParam MultiValueMap<String, String> p,
                                    Authentication auth, RedirectAttributes ra) {
        HorarioForm f = horario(p);
        return ejecutar(ra, "/medico/horarios", () -> {
            horarios.actualizar(auth.getName(), id, f);
            return "Franja actualizada.";
        });
    }

    @PostMapping("/horarios/{id}/estado")
    public String alternarHorario(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        return ejecutar(ra, "/medico/horarios", () ->
                horarios.alternar(auth.getName(), id) ? "Franja activada." : "Franja desactivada: ya no recibe citas.");
    }

    @PostMapping("/horarios/{id}/eliminar")
    public String eliminarHorario(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        return ejecutar(ra, "/medico/horarios", () -> {
            horarios.eliminar(auth.getName(), id);
            return "Franja eliminada.";
        });
    }

    // ================== SEDES ==================
    @GetMapping("/sedes")
    public String verSedes(Authentication auth, Model model) {
        if (!esMedico(auth)) return "redirect:/account";
        model.addAttribute("s", sedes.ver(auth.getName()));
        model.addAttribute("seccion", "sedes");
        return "medico/sedes";
    }

    @PostMapping("/sedes")
    public String crearSede(@RequestParam Map<String, String> p, Authentication auth, RedirectAttributes ra) {
        return ejecutar(ra, "/medico/sedes", () -> {
            sedes.crear(auth.getName(), sede(p));
            return "Sede agregada.";
        });
    }

    @PostMapping("/sedes/principal")
    public String sedePrincipal(@RequestParam Map<String, String> p, Authentication auth, RedirectAttributes ra) {
        return ejecutar(ra, "/medico/sedes", () -> {
            sedes.establecerPrincipal(auth.getName(), largo(p.get("establecimientoId")));
            return "Tu sede principal se actualizó.";
        });
    }

    @PostMapping("/sedes/{id}")
    public String actualizarSede(@PathVariable Long id, @RequestParam Map<String, String> p,
                                 Authentication auth, RedirectAttributes ra) {
        return ejecutar(ra, "/medico/sedes", () -> {
            sedes.actualizar(auth.getName(), id, sede(p));
            return "Sede actualizada.";
        });
    }

    @PostMapping("/sedes/{id}/eliminar")
    public String eliminarSede(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        return ejecutar(ra, "/medico/sedes", () -> {
            sedes.eliminar(auth.getName(), id);
            return "Sede eliminada.";
        });
    }

    // ================== RESEÑAS ==================
    @GetMapping("/resenas")
    public String verResenas(@RequestParam(defaultValue = "todas") String filtro, Authentication auth, Model model) {
        if (!esMedico(auth)) return "redirect:/account";
        model.addAttribute("r", resenas.ver(auth.getName(), filtro));
        model.addAttribute("seccion", "resenas");
        return "medico/resenas";
    }

    @PostMapping("/resenas/{id}/visibilidad")
    public String visibilidad(@PathVariable Long id, @RequestParam Map<String, String> p,
                              Authentication auth, RedirectAttributes ra) {
        boolean visible = "true".equals(p.get("visible"));
        String filtro = switch (p.getOrDefault("filtro", "todas")) {
            case "visibles" -> "visibles";
            case "ocultas" -> "ocultas";
            default -> "todas";
        };
        return ejecutar(ra, "/medico/resenas?filtro=" + filtro, () -> {
            resenas.cambiarVisibilidad(auth.getName(), id, visible);
            return visible ? "La opinión ahora se muestra en tu perfil público."
                    : "La opinión se ocultó de tu perfil público. La reseña original se conserva.";
        });
    }

    // ================== Auxiliares ==================
    /** Ejecuta la acción; éxito → "ok", error de negocio → "error". Siempre redirige (PRG). */
    private String ejecutar(RedirectAttributes ra, String destino, Supplier<String> accion) {
        try {
            ra.addFlashAttribute("ok", accion.get());
        } catch (NegocioException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + destino;
    }

    private HorarioForm horario(MultiValueMap<String, String> p) {
        List<Integer> dias = new ArrayList<>();
        List<String> crudos = p.get("dias");
        if (crudos != null) {
            for (String d : crudos) {
                Integer n = entero(d);
                if (n != null) dias.add(n);
            }
        }
        return new HorarioForm(dias, p.getFirst("horaInicio"), p.getFirst("horaFin"),
                entero(p.getFirst("duracionMin")), p.getFirst("modalidad"), "on".equals(p.getFirst("activo"))
                        || "true".equals(p.getFirst("activo")));
    }

    private SedeForm sede(Map<String, String> p) {
        return new SedeForm(p.get("nombre"), p.get("direccion"), p.get("referencia"), largo(p.get("distritoId")));
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
