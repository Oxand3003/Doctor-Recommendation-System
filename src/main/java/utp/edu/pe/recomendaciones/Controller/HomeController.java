package utp.edu.pe.recomendaciones.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;

import utp.edu.pe.recomendaciones.model.enums.MotivoConsulta;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;

/** Páginas públicas simples. */
@Controller
public class HomeController {

    private final EspecialidadRepository especialidades;
    private final List<String> distritosPermitidos;

    public HomeController(EspecialidadRepository especialidades,
                          @Value("${medicerca.distritos-permitidos}") List<String> distritosPermitidos) {
        this.especialidades = especialidades;
        this.distritosPermitidos = distritosPermitidos;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("specialties", especialidades.findByActivaTrueOrderByNombreAsc());
        model.addAttribute("searchDistricts", distritosPermitidos);
        model.addAttribute("motivosConsulta", MotivoConsulta.values());
        // El formulario de contacto también se renderiza en la carga inicial.
        // En caso de volver desde una validación fallida, se conservan los
        // valores enviados mediante el flash attribute.
        if (!model.containsAttribute("consultaForm")) {
            model.addAttribute("consultaForm", Map.of(
                    "nombres", "",
                    "correo", "",
                    "telefono", "",
                    "motivo", "",
                    "mensaje", ""));
        }
        model.addAttribute("specialtyLabels", Map.of(
                "Cardiología", "Cardiología",
                "Odontología", "Odontología",
                "Neurología", "Neurología",
                "Pediatría", "Pediatría",
                "Medicina General", "Medicina general",
                "Oftalmología", "Oftalmología",
                "Traumatología", "Traumatología",
                "Dermatología", "Dermatología"));
        model.addAttribute("specialtyDescriptions", Map.of(
                "Cardiología", "Corazón y presión arterial",
                "Odontología", "Salud dental y ortodoncia",
                "Neurología", "Sistema nervioso",
                "Pediatría", "Cuidado de los más pequeños",
                "Medicina General", "Chequeos y diagnóstico",
                "Oftalmología", "Salud visual",
                "Traumatología", "Huesos y articulaciones",
                "Dermatología", "Piel, cabello y uñas"));
        return "home/index";
    }
}
