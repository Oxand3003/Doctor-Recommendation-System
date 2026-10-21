package utp.edu.pe.recomendaciones.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.dto.DoctorProfileView;
import utp.edu.pe.recomendaciones.dto.DoctorSearchResult;
import utp.edu.pe.recomendaciones.dto.SlotView;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;

@Service
public class DoctorSearchService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final MedicoRepository medicos;
    private final DoctorProfileService profiles;
    private final Set<String> distritosPermitidos;

    public DoctorSearchService(MedicoRepository medicos, DoctorProfileService profiles,
                               @Value("${medicerca.distritos-permitidos}") List<String> distritosPermitidos) {
        this.medicos = medicos;
        this.profiles = profiles;
        this.distritosPermitidos = distritosPermitidos.stream()
                .map(this::normalize).collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public List<DoctorSearchResult> search(String specialty, String district, String clinic, LocalDate date, LocalTime time) {
        if (time != null && date == null) {
            throw new IllegalArgumentException("Selecciona una fecha para filtrar por horario.");
        }
        if (date != null && date.isBefore(LocalDate.now(LIMA))) {
            throw new IllegalArgumentException("La fecha debe ser hoy o posterior.");
        }

        String specialtyFilter = normalize(specialty);
        String districtFilter = normalize(district);
        String clinicFilter = normalize(clinic);
        if (!districtFilter.isEmpty() && !distritosPermitidos.contains(districtFilter)) {
            throw new IllegalArgumentException("El distrito seleccionado no está permitido.");
        }
        return medicos.findByUsuarioEstadoOrderByRatingPromedioDesc(EstadoUsuario.ACTIVO).stream()
                .filter(m -> specialtyFilter.isEmpty() || normalize(m.getEspecialidad().getNombre()).contains(specialtyFilter))
                .filter(m -> districtFilter.isEmpty() || (m.getEstablecimiento() != null
                        && m.getEstablecimiento().getDistrito() != null
                        && normalize(m.getEstablecimiento().getDistrito().getNombre()).contains(districtFilter)))
                .filter(m -> clinicFilter.isEmpty() || (m.getEstablecimiento() != null
                        && normalize(m.getEstablecimiento().getNombre()).contains(clinicFilter)))
                .map(m -> result(m, date, time))
                .filter(r -> r != null)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> clinicOptions() {
        return medicos.findByUsuarioEstadoOrderByRatingPromedioDesc(EstadoUsuario.ACTIVO).stream()
                .map(Medico::getEstablecimiento)
                .filter(java.util.Objects::nonNull)
                .map(e -> e.getNombre())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> districtOptions() {
        return distritosPermitidos.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private DoctorSearchResult result(Medico medico, LocalDate date, LocalTime time) {
        List<String> times = date == null ? List.of() : profiles.getSlots(medico.getId(), date, null).stream()
                .filter(SlotView::disponible)
                .map(SlotView::hora)
                // "Desde la hora": los turnos reales no siempre caen en la hora exacta (p. ej. 09:40, 10:20).
                .filter(hour -> time == null || !LocalTime.parse(hour).isBefore(time))
                .toList();
        if (date != null && times.isEmpty()) return null;

        DoctorProfileView profile = profiles.getProfile(medico.getId());
        String district = profile.consultorio() == null ? null : profile.consultorio().distrito();
        String clinic = profile.consultorio() == null ? profile.sede() : profile.consultorio().nombre();
        return new DoctorSearchResult(profile.id(), profile.nombreCompleto(), profile.iniciales(),
                profile.especialidad(), clinic, district, profile.rating(), profile.totalValoraciones(),
                profile.precio(), profile.fotoPerfilId() == null ? null : "/doctor/foto/" + profile.fotoPerfilId(), times);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
