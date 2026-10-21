package utp.edu.pe.recomendaciones.Controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import utp.edu.pe.recomendaciones.service.CuentaService;
import utp.edu.pe.recomendaciones.service.DoctorSearchService;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;

@Controller
public class AccountController {

    private final CuentaService cuentas;
    private final EspecialidadRepository especialidades;
    private final DoctorSearchService doctorSearch;

    public AccountController(CuentaService cuentas, EspecialidadRepository especialidades,
                             DoctorSearchService doctorSearch) {
        this.cuentas = cuentas;
        this.especialidades = especialidades;
        this.doctorSearch = doctorSearch;
    }

    @GetMapping("/account")
    public String account(Authentication auth, Model model,
                          @RequestParam(name = "modo", required = false) String modo) {
        // Los médicos entran directo a su perfil privado (/medico/perfil).
        boolean medico = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).anyMatch("ROLE_MEDICO"::equals);
        if (medico) {
            return "redirect:/medico/perfil";
        }
        model.addAttribute("cuenta", cuentas.cuenta(auth.getName()));
        model.addAttribute("searchSpecialties", especialidades.findByActivaTrueOrderByNombreAsc());
        model.addAttribute("searchClinics", doctorSearch.clinicOptions());
        model.addAttribute("searchDistricts", doctorSearch.districtOptions());
        // Sin parámetro se abre la búsqueda manual para que la página nunca quede vacía.
        model.addAttribute("modoBusqueda", "ia".equals(modo) ? "ia" : "manual");
        return "account/account";
    }
}
