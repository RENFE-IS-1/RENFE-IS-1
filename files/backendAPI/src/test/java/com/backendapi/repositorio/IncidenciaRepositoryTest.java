package com.backendapi.repositorio;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.backendapi.modelo.Incidencia;
import com.backendapi.modelo.OrigenIncidencia;

/**
 * Tests de IncidenciaRepository (US-017).
 *
 * Usa una base de datos H2 en memoria propia, para no escribir incidencias de prueba en el archivo
 * real data/usuarios_db. La descarga de datos de Renfe se desactiva, así que no necesita Internet.
 */
@SpringBootTest(properties = {
        "renfe.api.activo=false",
        "spring.datasource.url=jdbc:h2:mem:incidencias_test;DB_CLOSE_DELAY=-1"
})
class IncidenciaRepositoryTest {

    /** Hora fija de referencia para que los tests no dependan de la hora a la que se ejecutan. */
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 22, 8, 0);

    @Autowired
    private IncidenciaRepository repositorio;

    /** Vacía la tabla antes de cada test para que unos tests no influyan en otros. */
    @BeforeEach
    void vaciarTabla() {
        repositorio.deleteAll();
    }

    /** Crea y guarda una incidencia activa; lo usan todos los tests para preparar sus datos. */
    private Incidencia guardar(String linea, OrigenIncidencia origen, String idExterno, LocalDateTime inicio) {
        String tipo = origen == OrigenIncidencia.DETECCION_PROPIA ? "RETRASO" : "Aviso";
        return repositorio.save(new Incidencia(linea, tipo, "Descripción de prueba", origen, idExterno, inicio));
    }

    /** Crea, finaliza y guarda una incidencia; lo usan los tests que necesitan incidencias cerradas. */
    private Incidencia guardarFinalizada(String linea, OrigenIncidencia origen, String idExterno,
                                         LocalDateTime inicio, LocalDateTime fin) {
        Incidencia i = new Incidencia(linea, "Aviso", "Descripción de prueba", origen, idExterno, inicio);
        i.finalizar(fin);
        return repositorio.save(i);
    }

    // --- Guardado de incidencias finalizadas ---

    /** Comprueba que una incidencia cerrada no se borra: queda guardada con su hora de fin (criterio 4). */
    @Test
    void save_incidenciaFinalizada_seConservaConSuHoraDeFin() {
        Incidencia activa = guardar("C3", OrigenIncidencia.DETECCION_PROPIA, null, BASE);

        activa.finalizar(BASE.plusMinutes(30));
        repositorio.save(activa);

        Incidencia leida = repositorio.findById(activa.getId()).orElseThrow();
        assertFalse(leida.isActiva());
        assertEquals(BASE.plusMinutes(30), leida.getFin());
        assertEquals(1, repositorio.count());
    }

    // --- findByIdExternoAndFinIsNull ---

    /** Comprueba que encuentra un aviso oficial activo por su id de Renfe, para no duplicarlo. */
    @Test
    void findByIdExternoAndFinIsNull_avisoActivo_loEncuentra() {
        Incidencia aviso = guardar("C5", OrigenIncidencia.AVISO_OFICIAL, "ALERTA_1", BASE);

        Optional<Incidencia> resultado = repositorio.findByIdExternoAndFinIsNull("ALERTA_1");

        assertTrue(resultado.isPresent());
        assertEquals(aviso.getId(), resultado.get().getId());
    }

    /** Comprueba que un aviso ya finalizado no cuenta como activo: si reaparece, será uno nuevo. */
    @Test
    void findByIdExternoAndFinIsNull_avisoFinalizado_noLoDevuelve() {
        guardarFinalizada("C5", OrigenIncidencia.AVISO_OFICIAL, "ALERTA_1", BASE, BASE.plusHours(1));

        assertTrue(repositorio.findByIdExternoAndFinIsNull("ALERTA_1").isEmpty());
    }

    /** Comprueba que no devuelve nada para un id que no está guardado. */
    @Test
    void findByIdExternoAndFinIsNull_idDesconocido_noDevuelveNada() {
        guardar("C5", OrigenIncidencia.AVISO_OFICIAL, "ALERTA_1", BASE);

        assertTrue(repositorio.findByIdExternoAndFinIsNull("OTRA_ALERTA").isEmpty());
    }

    // --- findByLineaAndOrigenAndFinIsNull ---

    /** Comprueba que encuentra la incidencia por retraso activa de una línea, para no duplicarla. */
    @Test
    void findByLineaAndOrigenAndFinIsNull_retrasoActivo_loEncuentra() {
        Incidencia retraso = guardar("C3", OrigenIncidencia.DETECCION_PROPIA, null, BASE);

        Optional<Incidencia> resultado =
                repositorio.findByLineaAndOrigenAndFinIsNull("C3", OrigenIncidencia.DETECCION_PROPIA);

        assertTrue(resultado.isPresent());
        assertEquals(retraso.getId(), resultado.get().getId());
    }

    /** Comprueba que no mezcla líneas, orígenes ni incidencias ya finalizadas. */
    @Test
    void findByLineaAndOrigenAndFinIsNull_otraLineaOtroOrigenOFinalizada_noLasDevuelve() {
        guardar("C4", OrigenIncidencia.DETECCION_PROPIA, null, BASE);
        guardar("C3", OrigenIncidencia.AVISO_OFICIAL, "ALERTA_1", BASE);
        guardarFinalizada("C3", OrigenIncidencia.DETECCION_PROPIA, null, BASE, BASE.plusMinutes(20));

        assertTrue(repositorio.findByLineaAndOrigenAndFinIsNull("C3", OrigenIncidencia.DETECCION_PROPIA).isEmpty());
    }

    // --- findByOrigenAndFinIsNull ---

    /** Comprueba que devuelve solo las activas del origen pedido, que son las que habrá que revisar para cerrarlas. */
    @Test
    void findByOrigenAndFinIsNull_devuelveSoloActivasDeEseOrigen() {
        Incidencia c3 = guardar("C3", OrigenIncidencia.DETECCION_PROPIA, null, BASE);
        Incidencia c4 = guardar("C4", OrigenIncidencia.DETECCION_PROPIA, null, BASE.plusMinutes(5));
        guardar("C5", OrigenIncidencia.AVISO_OFICIAL, "ALERTA_1", BASE);
        guardarFinalizada("C1", OrigenIncidencia.DETECCION_PROPIA, null, BASE, BASE.plusMinutes(10));

        List<Incidencia> resultado = repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.DETECCION_PROPIA);

        assertEquals(2, resultado.size());
        assertTrue(resultado.stream().anyMatch(i -> i.getId().equals(c3.getId())));
        assertTrue(resultado.stream().anyMatch(i -> i.getId().equals(c4.getId())));
    }

    /** Comprueba que devuelve una lista vacía (no null) cuando no hay incidencias activas. */
    @Test
    void findByOrigenAndFinIsNull_sinActivas_devuelveListaVacia() {
        guardarFinalizada("C1", OrigenIncidencia.AVISO_OFICIAL, "ALERTA_1", BASE, BASE.plusMinutes(10));

        assertTrue(repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.AVISO_OFICIAL).isEmpty());
    }

    // --- findByFinIsNullOrderByInicioDesc ---

    /** Comprueba que devuelve solo las activas, de cualquier origen, de la más reciente a la más antigua. */
    @Test
    void findByFinIsNullOrderByInicioDesc_soloActivasOrdenadasPorInicioDescendente() {
        Incidencia antigua = guardar("C3", OrigenIncidencia.DETECCION_PROPIA, null, BASE);
        Incidencia reciente = guardar("C5", OrigenIncidencia.AVISO_OFICIAL, "ALERTA_1", BASE.plusHours(2));
        Incidencia intermedia = guardar("C4", OrigenIncidencia.DETECCION_PROPIA, null, BASE.plusHours(1));
        guardarFinalizada("C1", OrigenIncidencia.AVISO_OFICIAL, "ALERTA_2", BASE.plusHours(3), BASE.plusHours(4));

        List<Incidencia> resultado = repositorio.findByFinIsNullOrderByInicioDesc();

        assertEquals(List.of(reciente.getId(), intermedia.getId(), antigua.getId()),
                resultado.stream().map(Incidencia::getId).toList());
    }

    // --- findByLineaOrderByInicioDesc ---

    /** Comprueba que devuelve activas y finalizadas de una línea, ordenadas, sin incluir otras líneas. */
    @Test
    void findByLineaOrderByInicioDesc_activasYFinalizadasDeLaLineaOrdenadas() {
        Incidencia cerrada = guardarFinalizada("C3", OrigenIncidencia.AVISO_OFICIAL, "ALERTA_1", BASE, BASE.plusHours(1));
        Incidencia activa = guardar("C3", OrigenIncidencia.DETECCION_PROPIA, null, BASE.plusHours(2));
        guardar("C4", OrigenIncidencia.DETECCION_PROPIA, null, BASE.plusHours(3));

        List<Incidencia> resultado = repositorio.findByLineaOrderByInicioDesc("C3");

        assertEquals(List.of(activa.getId(), cerrada.getId()),
                resultado.stream().map(Incidencia::getId).toList());
        assertTrue(resultado.get(0).isActiva());
        assertFalse(resultado.get(1).isActiva());
    }

    /** Comprueba que una línea sin incidencias devuelve una lista vacía. */
    @Test
    void findByLineaOrderByInicioDesc_lineaSinIncidencias_devuelveListaVacia() {
        guardar("C3", OrigenIncidencia.DETECCION_PROPIA, null, BASE);

        assertTrue(repositorio.findByLineaOrderByInicioDesc("C10").isEmpty());
    }
}
