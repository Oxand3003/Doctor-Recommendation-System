package utp.edu.pe.recomendaciones.dto;

import java.util.List;

/** Todo lo que necesita la plantilla medico/perfil.html (perfil privado del médico). */
public record PerfilPrivadoView(
        Long medicoId,
        String nombreCompleto,
        String iniciales,
        String especialidad,
        String subtitulo,
        String email,
        String dni,
        String telefono,
        String cmp,
        String rne,
        String sede,
        String costoConsulta,
        String rating,
        int totalValoraciones,
        Long fotoPerfilId,
        List<Long> galeria,
        int maxGaleria,
        PerfilMedicoForm datos,
        List<FormacionItem> formaciones,
        List<OpcionView> especialidades,
        List<OpcionView> establecimientos,
        List<TipoOpcion> tiposFormacion) {

    public record FormacionItem(Long id, String tipo, String tipoLabel, String titulo,
                                String institucion, Integer anio) { }

    public record TipoOpcion(String valor, String label) { }

    /** Misma vista pero con otros valores en el formulario (p. ej. tras un error de validación). */
    public PerfilPrivadoView conDatos(PerfilMedicoForm otros) {
        return new PerfilPrivadoView(medicoId, nombreCompleto, iniciales, especialidad, subtitulo, email, dni,
                telefono, cmp, rne, sede, costoConsulta, rating, totalValoraciones, fotoPerfilId, galeria, maxGaleria, otros,
                formaciones, especialidades, establecimientos, tiposFormacion);
    }
}
