package utp.edu.pe.recomendaciones.Controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import utp.edu.pe.recomendaciones.dto.DoctorSearchResult;
import utp.edu.pe.recomendaciones.service.DoctorSearchService;

@RestController
@RequestMapping("/api/doctors")
public class DoctorSearchController {
    private final DoctorSearchService searchService;

    public DoctorSearchController(DoctorSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public List<DoctorSearchResult> search(
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String clinic,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime time) {
        try {
            return searchService.search(specialty, district, clinic, date, time);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}
