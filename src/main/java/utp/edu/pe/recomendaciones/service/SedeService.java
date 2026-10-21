package utp.edu.pe.recomendaciones.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.dto.OpcionView;
import utp.edu.pe.recomendaciones.dto.SedeForm;
import utp.edu.pe.recomendaciones.dto.SedesView;
import utp.edu.pe.recomendaciones.dto.SedesView.SedeItem;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Distrito;
import utp.edu.pe.recomendaciones.model.Establecimiento;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.repository.DistritoRepository;
import utp.edu.pe.recomendaciones.repository.EstablecimientoRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;

/**
 * Sedes del médico. Establecimiento.propietario marca quién creó la sede:
 *  - propietario null  → sede del catálogo: cualquier médico puede elegirla como principal, nadie la edita aquí.
 *  - propietario = yo  → solo yo la veo, la elijo, la edito y la elimino (findByIdAndPropietarioId).
 * Así ningún médico puede alterar ni apoyarse en las sedes de otro, y nunca queda una sede borrada
 * que otro médico siga usando (se bloquea el borrado si hay otro médico apuntando a ella).
 * La sede "principal" es Medico.establecimiento (lo que ya muestra el perfil público).
 */
@Service
public class SedeService {

    public static final int MAX_SEDES = 10;

    private final MedicoSesion sesion;
    private final MedicoRepository medicos;
    private final EstablecimientoRepository sedes;
    private final DistritoRepository distritos;
    private final List<String> distritosPermitidos;

    public SedeService(MedicoSesion sesion, MedicoRepository medicos, EstablecimientoRepository sedes,
                       DistritoRepository distritos,
                       @Value("${medicerca.distritos-permitidos}") List<String> distritosPermitidos) {
        this.sesion = sesion;
        this.medicos = medicos;
        this.sedes = sedes;
        this.distritos = distritos;
        this.distritosPermitidos = distritosPermitidos;
    }

    // ------------------------------------------------------------------
    // LECTURA
    // ------------------------------------------------------------------
    @Transactional(readOnly = true)
    public SedesView ver(String email) {
        Medico m = sesion.actual(email);
        Long principalId = m.getEstablecimiento() != null ? m.getEstablecimiento().getId() : null;
        List<SedeItem> propias = sedes.findByPropietarioIdOrderByNombreAsc(m.getId()).stream()
                .map(e -> new SedeItem(e.getId(), e.getNombre(), e.getDireccion(), e.getReferencia(),
                        e.getDistrito().getId(), etiquetaDistrito(e.getDistrito()), e.getId().equals(principalId)))
                .toList();
        List<OpcionView> listaDistritos = distritos.findAll(Sort.by("nombre")).stream()
                .filter(d -> distritosPermitidos.stream().anyMatch(nombre -> nombre.equalsIgnoreCase(d.getNombre())))
                .map(d -> new OpcionView(d.getId(), etiquetaDistrito(d))).toList();
        return new SedesView(propias, principalId,
                m.getEstablecimiento() != null ? m.getEstablecimiento().getNombre() : "Por confirmar",
                opciones(m), listaDistritos, MAX_SEDES);
    }

    /**
     * Sedes que este médico puede elegir como principal: las del catálogo y las suyas.
     * Debe llamarse dentro de una transacción (lee el distrito, que es lazy).
     */
    public List<OpcionView> opciones(Medico m) {
        List<OpcionView> out = new ArrayList<>();
        sedes.findByPropietarioIsNull(Sort.by("nombre")).stream()
                .filter(e -> esDistritoPermitido(e.getDistrito())).forEach(e ->
                out.add(new OpcionView(e.getId(), e.getNombre() + " — " + e.getDistrito().getNombre())));
        sedes.findByPropietarioIdOrderByNombreAsc(m.getId()).forEach(e ->
                out.add(new OpcionView(e.getId(), e.getNombre() + " — " + e.getDistrito().getNombre() + " (mi sede)")));
        return out;
    }

    /** ¿Puede este médico usar esa sede como principal? Catálogo o propia; nunca la de otro médico. */
    public boolean esElegible(Establecimiento e, Medico m) {
        return esDistritoPermitido(e.getDistrito())
                && (e.getPropietario() == null || e.getPropietario().getId().equals(m.getId()));
    }

    // ------------------------------------------------------------------
    // ESCRITURA
    // ------------------------------------------------------------------
    @Transactional
    public void crear(String email, SedeForm f) {
        Medico m = sesion.actual(email);
        if (sedes.countByPropietarioId(m.getId()) >= MAX_SEDES) {
            throw new NegocioException("Puedes registrar hasta " + MAX_SEDES + " sedes propias.");
        }
        Establecimiento e = new Establecimiento();
        e.setPropietario(m);
        aplicar(e, m, f, null);
        Establecimiento guardada = sedes.save(e);
        // Si aún no tenía sede principal, la primera sede que crea pasa a serlo.
        if (m.getEstablecimiento() == null) {
            m.setEstablecimiento(guardada);
            medicos.save(m);
        }
    }

    @Transactional
    public void actualizar(String email, Long id, SedeForm f) {
        Medico m = sesion.actual(email);
        Establecimiento e = propia(id, m);
        aplicar(e, m, f, e.getId());
        sedes.save(e);
    }

    @Transactional
    public void eliminar(String email, Long id) {
        Medico m = sesion.actual(email);
        Establecimiento e = propia(id, m);
        boolean esMiPrincipal = m.getEstablecimiento() != null && id.equals(m.getEstablecimiento().getId());
        long enUso = medicos.countByEstablecimientoId(id) - (esMiPrincipal ? 1 : 0);
        if (enUso > 0) {
            throw new NegocioException("No se puede eliminar la sede porque otro médico la está usando.");
        }
        if (esMiPrincipal) {
            m.setEstablecimiento(null);
            medicos.save(m);
        }
        sedes.delete(e);
    }

    /** idSede null = "Por confirmar" (sin sede principal). */
    @Transactional
    public void establecerPrincipal(String email, Long idSede) {
        Medico m = sesion.actual(email);
        if (idSede == null) {
            m.setEstablecimiento(null);
        } else {
            Establecimiento e = sedes.findById(idSede)
                    .filter(x -> esElegible(x, m))
                    .orElseThrow(() -> new NegocioException("La sede seleccionada no existe."));
            m.setEstablecimiento(e);
        }
        medicos.save(m);
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------
    private Establecimiento propia(Long id, Medico m) {
        return sedes.findByIdAndPropietarioId(id, m.getId())
                .orElseThrow(() -> new NegocioException("La sede indicada no existe o no es tuya."));
    }

    private void aplicar(Establecimiento e, Medico m, SedeForm f, Long excluirId) {
        String nombre = exigir(f.nombre(), "El nombre de la sede", 150);
        String direccion = exigir(f.direccion(), "La dirección", 200);
        String referencia = opcional(f.referencia(), "La referencia", 200);
        if (f.distritoId() == null) throw new NegocioException("Selecciona un distrito.");
        Distrito distrito = distritos.findById(f.distritoId())
                .orElseThrow(() -> new NegocioException("El distrito seleccionado no existe."));
        if (distritosPermitidos.stream().noneMatch(permitido -> permitido.equalsIgnoreCase(distrito.getNombre()))) {
            throw new NegocioException("El distrito seleccionado no está permitido.");
        }
        boolean repetida = sedes.findByPropietarioIdOrderByNombreAsc(m.getId()).stream()
                .anyMatch(o -> !o.getId().equals(excluirId) && o.getNombre().equalsIgnoreCase(nombre)
                        && o.getDistrito().getId().equals(distrito.getId()));
        if (repetida) throw new NegocioException("Ya tienes una sede con ese nombre en ese distrito.");
        e.setNombre(nombre);
        e.setDireccion(direccion);
        e.setReferencia(referencia.isEmpty() ? null : referencia);
        e.setDistrito(distrito);
    }

    private String etiquetaDistrito(Distrito d) {
        return d.getNombre() + ", " + d.getProvincia();
    }

    private boolean esDistritoPermitido(Distrito distrito) {
        return distrito != null && distritosPermitidos.stream()
                .anyMatch(permitido -> permitido.equalsIgnoreCase(distrito.getNombre()));
    }

    private String exigir(String valor, String campo, int max) {
        String v = limpiar(valor);
        if (v.isEmpty()) throw new NegocioException(campo + " es obligatorio.");
        if (v.length() > max) throw new NegocioException(campo + " no puede superar " + max + " caracteres.");
        return v;
    }

    private String opcional(String valor, String campo, int max) {
        String v = limpiar(valor);
        if (v.length() > max) throw new NegocioException(campo + " no puede superar " + max + " caracteres.");
        return v;
    }

    private String limpiar(String valor) {
        return valor == null ? "" : valor.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s{2,}", " ");
    }
}
