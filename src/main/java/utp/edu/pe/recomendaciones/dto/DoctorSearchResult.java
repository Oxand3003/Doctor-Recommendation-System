package utp.edu.pe.recomendaciones.dto;

import java.util.List;

/** Datos reales que necesita una tarjeta de resultados del inicio. */
public record DoctorSearchResult(
        Long id,
        String name,
        String initials,
        String specialty,
        String clinic,
        String district,
        String rating,
        int reviews,
        String price,
        String photoUrl,
        List<String> availableTimes) { }
