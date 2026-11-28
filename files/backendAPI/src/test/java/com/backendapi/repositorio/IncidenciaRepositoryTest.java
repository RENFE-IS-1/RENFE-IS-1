package com.backendapi.repositorio;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.backendapi.modelo.DeclaracionIncidencia;
import com.backendapi.modelo.Incidencia;
import com.backendapi.modelo.OrigenIncidencia;
import com.backendapi.modelo.RolUsuario;
import com.backendapi.modelo.TipoIncidenciaUsuario;
import com.backendapi.modelo.Usuario;

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

    @Autowired
    private DeclaracionIncidenciaRepository declaraciones;

    @Autowired
    private UsuarioRepository usuarios;

    /**
     * Vacía las tablas de incidencias antes de cada test para que unos tests no influyan en otros.
     * Primero las declaraciones, porque apuntan a las incidencias.
     */
    @BeforeEach
    void vaciarTabla() {
        declaraciones.deleteAll();
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

    // --- findByIdExternoAndLineaAndFinIsNull ---

    /** Comprueba que encuentra un aviso oficial activo por su id de Renfe y su línea, para no duplicarlo. */
    @Test
    void findByIdExternoAndLineaAndFinIsNull_avisoActivo_loEncuentra() {
        Incidencia aviso = guardar("C5", OrigenIncidencia.AVISO_OFICIAL, "ALERTA_1", BASE);

        Optional<Incidencia> resultado = repositorio.findByIdExternoAndLineaAndFinIsNull("ALERTA_1", "C5");

        assertTrue(resultado.isPresent());
        assertEquals(aviso.getId(), resultado.get().getId());
    }

    /** Comprueba que un mismo aviso en dos líneas son dos incidencias distintas y no se confunden. */
    @Test
    void findByIdExternoAndLineaAndFinIsNull_mismoAvisoEnDosLineas_devuelveLaDeCadaLinea() {
        Incidencia c3 = guardar("C3", OrigenIncidencia.AVISO_OFICIAL, "ALERTA_1", BASE);
        Incidencia c4 = guardar("C4", OrigenIncidencia.AVISO_OFICIAL, "ALERTA_1", BASE);

        assertEquals(c3.getId(), repositorio.findByIdExternoAndLineaAndFinIsNull("ALERTA_1", "C3").orElseThrow().getId());
        assertEquals(c4.getId(), repositorio.findByIdExternoAndLineaAndFinIsNull("ALERTA_1", "C4").orElseThrow().getId());
        assertTrue(repositorio.findByIdExternoAndLineaAndFinIsNull("ALERTA_1", "C5").isEmpty());
    }

    /** Comprueba que un aviso ya finalizado no cuenta como activo: si reaparece, será uno nuevo. */
    @Test
    void findByIdExternoAndLineaAndFinIsNull_avisoFinalizado_noLoDevuelve() {
        guardarFinalizada("C5", OrigenIncidencia.AVISO_OFICIAL, "ALERTA_1", BASE, BASE.plusHours(1));

        assertTrue(repositorio.findByIdExternoAndLineaAndFinIsNull("ALERTA_1", "C5").isEmpty());
    }

    /** Comprueba que no devuelve nada para un id que no está guardado. */
    @Test
    void findByIdExternoAndLineaAndFinIsNull_idDesconocido_noDevuelveNada() {
        guardar("C5", OrigenIncidencia.AVISO_OFICIAL, "ALERTA_1", BASE);

        assertTrue(repositorio.findByIdExternoAndLineaAndFinIsNull("OTRA_ALERTA", "C5").isEmpty());
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

    // --- findByLineaAndTipoAndOrigenAndFinIsNull ---

    /** Comprueba que encuentra el grupo activo de una línea y un tipo, donde se suman las declaraciones de los viajeros. */
    @Test
    void findByLineaAndTipoAndOrigenAndFinIsNull_encuentraElGrupoDeLaLineaYTipo() {
        Incidencia averia = repositorio.save(new Incidencia("C3", "AVERIA_TREN", "Avería en el tren",
                OrigenIncidencia.DECLARADA_POR_USUARIO, null, BASE));
        repositorio.save(new Incidencia("C3", "AGLOMERACION", "Aglomeración",
                OrigenIncidencia.DECLARADA_POR_USUARIO, null, BASE));

        Optional<Incidencia> resultado = repositorio.findByLineaAndTipoAndOrigenAndFinIsNull(
                "C3", "AVERIA_TREN", OrigenIncidencia.DECLARADA_POR_USUARIO);

        assertTrue(resultado.isPresent());
        assertEquals(averia.getId(), resultado.get().getId());
    }

    /** Comprueba que un grupo ya cerrado no se reutiliza: una nueva declaración empezará otro grupo. */
    @Test
    void findByLineaAndTipoAndOrigenAndFinIsNull_grupoCerrado_noLoDevuelve() {
        Incidencia cerrada = new Incidencia("C3", "AVERIA_TREN", "Avería en el tren",
                OrigenIncidencia.DECLARADA_POR_USUARIO, null, BASE);
        cerrada.finalizar(BASE.plusHours(1));
        repositorio.save(cerrada);

        assertTrue(repositorio.findByLineaAndTipoAndOrigenAndFinIsNull(
                "C3", "AVERIA_TREN", OrigenIncidencia.DECLARADA_POR_USUARIO).isEmpty());
    }

    /** Comprueba que el número de declarantes se guarda y se lee correctamente de la base de datos. */
    @Test
    void save_incidenciaDeclarada_conservaElNumeroDeDeclarantes() {
        Incidencia grupo = new Incidencia("C3", "AVERIA_TREN", "Avería en el tren",
                OrigenIncidencia.DECLARADA_POR_USUARIO, null, BASE);
        grupo.sumarDeclarante(BASE);
        grupo.sumarDeclarante(BASE.plusMinutes(1));
        repositorio.save(grupo);

        Incidencia leida = repositorio.findById(grupo.getId()).orElseThrow();
        assertEquals(2, leida.getNumeroDeclarantes());
    }

    // --- DeclaracionIncidenciaRepository ---

    /** Crea y guarda un viajero de prueba con un email único; lo usan los tests de declaraciones. */
    private Usuario guardarViajero() {
        return usuarios.save(new Usuario("Viajero", "viajero_" + System.nanoTime() + "@cercanias.es",
                "hash", RolUsuario.VIAJERO, true));
    }

    /** Comprueba que se detecta si un viajero ya declaró una incidencia, para no contarlo dos veces. */
    @Test
    void existsByIncidenciaIdAndUsuarioId_detectaSiElViajeroYaDeclaro() {
        Usuario viajero = guardarViajero();
        Usuario otro = guardarViajero();
        Incidencia grupo = repositorio.save(new Incidencia("C3", "AVERIA_TREN", "Avería en el tren",
                OrigenIncidencia.DECLARADA_POR_USUARIO, null, BASE));
        declaraciones.save(new DeclaracionIncidencia(grupo, viajero, TipoIncidenciaUsuario.AVERIA_TREN,
                null, null, BASE));

        assertTrue(declaraciones.existsByIncidenciaIdAndUsuarioId(grupo.getId(), viajero.getId()));
        assertFalse(declaraciones.existsByIncidenciaIdAndUsuarioId(grupo.getId(), otro.getId()));

        usuarios.delete(viajero);
        usuarios.delete(otro);
    }

    /** Comprueba que si el viajero borra su cuenta, su declaración y la incidencia se conservan sin el vínculo al usuario. */
    @Test
    void borrarUsuario_conservaSuDeclaracionSinUsuario() {
        Usuario viajero = guardarViajero();
        Incidencia grupo = repositorio.save(new Incidencia("C3", "AVERIA_TREN", "Avería en el tren",
                OrigenIncidencia.DECLARADA_POR_USUARIO, null, BASE));
        DeclaracionIncidencia declaracion = declaraciones.save(new DeclaracionIncidencia(grupo, viajero,
                TipoIncidenciaUsuario.AVERIA_TREN, null, "Comentario", BASE));

        usuarios.delete(viajero);

        DeclaracionIncidencia leida = declaraciones.findById(declaracion.getId()).orElseThrow();
        assertNull(leida.getUsuario());
        assertEquals("Comentario", leida.getComentario());
        assertTrue(repositorio.findById(grupo.getId()).isPresent());
    }
}
