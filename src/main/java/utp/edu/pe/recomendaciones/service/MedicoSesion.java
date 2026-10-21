package utp.edu.pe.recomendaciones.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.Usuario;
import utp.edu.pe.recomendaciones.model.enums.EstadoUsuario;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.repository.UsuarioRepository;

/**
 * Resuelve el médico dueño de la sesión (correo → Medico). Exige rol MEDICO y cuenta ACTIVA; si no, 403.
 * Es la única puerta por la que los servicios de gestión (tarifas, horarios, sedes, reseñas) obtienen
 * al médico: nunca reciben un id de médico desde el cliente. Llamar siempre dentro de una transacción.
 */
@Component
public class MedicoSesion {

    private final UsuarioRepository usuarios;
    private final MedicoRepository medicos;

    public MedicoSesion(UsuarioRepository usuarios, MedicoRepository medicos) {
        this.usuarios = usuarios;
        this.medicos = medicos;
    }

    public Medico actual(String email) {
        Usuario u = usuarios.findByEmail(email == null ? "" : email.trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (!"MEDICO".equals(u.getRol().getNombre()) || u.getEstado() != EstadoUsuario.ACTIVO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return medicos.findByUsuarioId(u.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
    }
}
