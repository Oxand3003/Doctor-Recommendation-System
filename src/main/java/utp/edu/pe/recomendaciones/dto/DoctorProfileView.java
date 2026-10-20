package utp.edu.pe.recomendaciones.dto;


import java.util.List;

/** Todo lo que necesita la plantilla doctor-profile.html. */
public record DoctorProfileView(
        Long id,
        String nombreCompleto,
        String iniciales,
        String especialidad,
        String subtitulo,
        String cmp,
        String rne,
        String sede,
        String biografia,
        String precio,
        String rating,
        String estrellas,
        int totalValoraciones,
        int aniosExperiencia,
        long pacientesAtendidos,
        int score,
        boolean disponibleHoy,
        boolean presencial,
        boolean teleconsulta,
        String modalidadInicial,
        String modalidadesTexto,
        List<FormacionView> formaciones,
        List<ServicioView> servicios,
        ConsultorioView consultorio,
        List<String> horarios,
        List<CriterioView> desglose,
        List<ReviewView> reviews,
        List<DayView> dias,
        String universidad,
        Long fotoPerfilId,
        List<Long> galeria,
        String precioPresencial,
        String precioTeleconsulta,
        String precioHora,
        String duracionConsulta,
        List<ConsultorioView> otrasSedes) {

    public record FormacionView(String titulo, String detalle) { }

    /** Precios por modalidad: precioPresencial/precioTeleconsulta ya vienen resueltos ("—" si no hay). */
    public record ServicioView(String nombre, String descripcion, String precio) { }

    public record ConsultorioView(String nombre, String direccion, String referencia, String distrito) { }

    public record CriterioView(String nombre, String valor, int porcentaje) { }

    public record ReviewView(String iniciales, String autor, String fecha, String modalidad,
                             String estrellas, String comentario) { }

    public record DayView(String fecha, String nombre, int numero, String sub, String etiqueta,
                          boolean disponible, boolean activo) { }
}