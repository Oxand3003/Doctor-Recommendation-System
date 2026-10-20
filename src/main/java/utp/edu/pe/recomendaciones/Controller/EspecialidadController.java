package utp.edu.pe.recomendaciones.Controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import utp.edu.pe.recomendaciones.dto.DoctorProfileView;
import utp.edu.pe.recomendaciones.model.Especialidad;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.repository.EspecialidadRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.service.DoctorProfileService;

@Controller
@RequestMapping("/especialidades")
public class EspecialidadController {
    private static final int PAGE_SIZE = 12;

    private final EspecialidadRepository especialidades;
    private final MedicoRepository medicos;
    private final DoctorProfileService profiles;

    public EspecialidadController(EspecialidadRepository especialidades,
                                  MedicoRepository medicos,
                                  DoctorProfileService profiles) {
        this.especialidades = especialidades;
        this.medicos = medicos;
        this.profiles = profiles;
    }

    @GetMapping("/{id}")
    public String listado(@PathVariable Long id,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        Especialidad especialidad = especialidades.findById(id)
                .filter(Especialidad::isActiva)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Especialidad no encontrada"));

        int pageIndex = Math.max(0, page);
        Page<Medico> resultado = medicos.findByEspecialidadIdAndUsuarioEstado(id, EstadoUsuario.ACTIVO,
                PageRequest.of(pageIndex, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "ratingPromedio")));
        if (resultado.getTotalPages() > 0 && pageIndex >= resultado.getTotalPages()) {
            pageIndex = resultado.getTotalPages() - 1;
            resultado = medicos.findByEspecialidadIdAndUsuarioEstado(id, EstadoUsuario.ACTIVO,
                    PageRequest.of(pageIndex, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "ratingPromedio")));
        }

        List<DoctorProfileView> doctors = resultado.getContent().stream()
                .map(medico -> profiles.getProfile(medico.getId()))
                .toList();
        model.addAttribute("specialty", especialidad);
        model.addAttribute("doctors", doctors);
        model.addAttribute("doctorPage", resultado);
        model.addAttribute("pageNumber", pageIndex);
        return "specialty/doctors";
    }
}
