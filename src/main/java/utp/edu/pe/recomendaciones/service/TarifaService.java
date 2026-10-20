package utp.edu.pe.recomendaciones.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.dto.TarifasForm;
import utp.edu.pe.recomendaciones.dto.TarifasView;
import utp.edu.pe.recomendaciones.exception.NegocioException;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.model.enums.Modalidad;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;

/**
 * Tarifas del médico. Se guardan en columnas de Medico para no romper nada de lo existente:
 *  - costoConsulta       → consulta presencial y precio base (lo que ya leían el perfil público y Cita.precio)
 *  - costoTeleconsulta   → teleconsulta; vacío = hereda costoConsulta (Medico.precioPara)
 *  - costoPorHora        → tarifa referencial por hora (informativa)
 * ServicioMedico.precio (procedimientos con precio propio) no se toca.
 */
@Service
public class TarifaService {

    private static final BigDecimal MAX = new BigDecimal("9999.99");

    private final MedicoSesion sesion;
    private final MedicoRepository medicos;

    public TarifaService(MedicoSesion sesion, MedicoRepository medicos) {
        this.sesion = sesion;
        this.medicos = medicos;
    }

    @Transactional(readOnly = true)
    public TarifasView ver(String email) {
        Medico m = sesion.actual(email);
        TarifasForm datos = new TarifasForm(texto(m.getCostoConsulta()), texto(m.getCostoTeleconsulta()),
                texto(m.getCostoPorHora()));
        BigDecimal tele = m.precioPara(Modalidad.TELECONSULTA);
        return new TarifasView(datos, texto(tele), m.getCostoTeleconsulta() == null, MAX.intValue());
    }

    @Transactional
    public void guardar(String email, TarifasForm f) {
        Medico m = sesion.actual(email);
        BigDecimal presencial = parsear(f.costoConsulta(), "El precio de consulta presencial");
        BigDecimal tele = parsear(f.costoTeleconsulta(), "El precio de teleconsulta");
        BigDecimal hora = parsear(f.costoPorHora(), "El precio por hora");
        m.setCostoConsulta(presencial);
        m.setCostoTeleconsulta(tele);
        m.setCostoPorHora(hora);
        medicos.save(m);
    }

    /** Vacío = sin definir (null). Entre 0 y 9999.99, máximo 2 decimales. */
    private BigDecimal parsear(String raw, String campo) {
        String v = raw == null ? "" : raw.trim().replace(',', '.');
        if (v.isEmpty()) return null;
        BigDecimal c;
        try {
            c = new BigDecimal(v);
        } catch (NumberFormatException e) {
            throw new NegocioException(campo + " no es válido.");
        }
        if (c.signum() < 0 || c.compareTo(MAX) > 0) {
            throw new NegocioException(campo + " debe estar entre 0 y 9999.99 soles.");
        }
        if (c.scale() > 2 && c.stripTrailingZeros().scale() > 2) {
            throw new NegocioException(campo + " admite como máximo 2 decimales.");
        }
        return c.setScale(2, RoundingMode.HALF_UP);
    }

    private String texto(BigDecimal p) {
        return p == null ? "" : p.stripTrailingZeros().toPlainString();
    }
}
