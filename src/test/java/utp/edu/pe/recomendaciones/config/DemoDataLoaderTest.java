package utp.edu.pe.recomendaciones.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import utp.edu.pe.recomendaciones.model.Distrito;
import utp.edu.pe.recomendaciones.model.Establecimiento;
import utp.edu.pe.recomendaciones.model.Medico;
import utp.edu.pe.recomendaciones.repository.DistritoRepository;
import utp.edu.pe.recomendaciones.repository.EstablecimientoRepository;
import utp.edu.pe.recomendaciones.repository.MedicoRepository;
import utp.edu.pe.recomendaciones.service.DoctorSearchService;

/**
 * La demo usa sedes de Trujillo, y una base cargada con la demo antigua (sedes de Lima) se corrige al arrancar.
 * <p>
 * Usa su propia base en memoria: las demás pruebas comparten "medicerca-tests" y, según el orden en que corran
 * (en GitHub Actions no es el mismo que en Windows), la recrean o le agregan médicos, y entonces la demo no
 * se carga (solo corre con la base vacía). Con una base propia esta prueba no depende del orden.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:demo-data-loader;DB_CLOSE_DELAY=-1",
        "medicerca.demo-data=true"})
@Transactional
class DemoDataLoaderTest {

    @Autowired DemoDataLoader demo;
    @Autowired MedicoRepository medicos;
    @Autowired DistritoRepository distritos;
    @Autowired EstablecimientoRepository establecimientos;
    @Autowired DoctorSearchService busqueda;

    @Test
    void lasSedesDeLaDemoEstanEnDistritosDelBuscador() {
        assertThat(busqueda.clinicOptions()).isNotEmpty()
                .allSatisfy(sede -> assertThat(busqueda.districtOptions())
                        .anySatisfy(d -> assertThat(d).isEqualToIgnoringCase(sede.district())));
    }

    @Test
    void unMedicoConLaSedeAntiguaDeLimaPasaASuSedeDeTrujillo() {
        Distrito sanBorja = distritos.findByNombreAndProvincia("San Borja", "Lima").orElseGet(() -> {
            Distrito d = new Distrito();
            d.setNombre("San Borja");
            d.setProvincia("Lima");
            d.setDepartamento("Lima");
            return distritos.save(d);
        });
        Establecimiento antigua = new Establecimiento();
        antigua.setNombre("Clínica San Borja");
        antigua.setDistrito(sanBorja);
        antigua = establecimientos.save(antigua);
        Medico medico = medicos.findAll().get(0);
        medico.setEstablecimiento(antigua);
        medicos.saveAndFlush(medico);
        // Los demás médicos, que ya están en sedes de Trujillo, no cambian.
        Medico otro = medicos.findAll().get(1);
        Establecimiento sedeDelOtro = otro.getEstablecimiento();

        demo.run(null);

        Establecimiento ahora = medicos.findById(medico.getId()).orElseThrow().getEstablecimiento();
        assertThat(ahora.getNombre()).isEqualTo("Clínica San Pablo");
        assertThat(ahora.getDistrito().getNombre()).isEqualTo("Trujillo");
        assertThat(medicos.findById(otro.getId()).orElseThrow().getEstablecimiento()).isEqualTo(sedeDelOtro);
    }
}
