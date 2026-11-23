package com.backendapi.servicio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.backendapi.modelo.Incidencia;
import com.backendapi.modelo.OrigenIncidencia;
import com.backendapi.repositorio.IncidenciaRepository;

import modelo.Linea;
import modelo.RetrasoParada;
import modelo.Viaje;
import repositorio.RedTransporte;

/**
 * Tests unitarios de ServicioIncidencias, parte 1: detección por retrasos (US-017, criterios 2, 3 y 4).
 *
 * Usa una RedTransporte de prueba con trenes inventados y un repositorio simulado (mock), así que no
 * necesita base de datos ni conexión a Internet.
 */
class ServicioIncidenciasTest {

    /** Hora fija de la revisión para que los tests no dependan de la hora a la que se ejecutan. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 23, 8, 0);

    private RedTransporte red;
    private IncidenciaRepository repositorio;
    private ServicioIncidencias servicio;

    /** Prepara una red con las líneas C3 y C4, datos en tiempo real disponibles y un repositorio simulado. */
    @BeforeEach
    void preparar() {
        red = new RedTransporte();
        red.addLinea(new Linea("R_C3", "C3", "Aranjuez - Chamartín", "8F2D86"));
        red.addLinea(new Linea("R_C4", "C4", "Parla - Colmenar Viejo", "2C5AA0"));
        red.setHorariosTiempoRealActivos(true);

        repositorio = mock(IncidenciaRepository.class);
        servicio = new ServicioIncidencias(repositorio, red);
    }

    /**
     * Crea en la red un tren de la línea indicada con el retraso indicado (en segundos).
     * Lo usan los tests para preparar los retrasos de cada caso.
     */
    private void anadirTrenes(Map<String, List<RetrasoParada>> retrasos, String routeId, int cantidad, int retrasoSegundos) {
        for (int i = 0; i < cantidad; i++) {
            String tripId = routeId + "_tren_" + retrasos.size();
            red.addViaje(new Viaje(tripId, routeId, "SVC"));
            retrasos.put(tripId, List.of(RetrasoParada.deTodoElViaje(retrasoSegundos)));
        }
    }

    /** Devuelve la única incidencia que el servicio ha guardado; lo usan los tests que comprueban qué se guarda. */
    private Incidencia incidenciaGuardada() {
        ArgumentCaptor<Incidencia> captor = ArgumentCaptor.forClass(Incidencia.class);
        verify(repositorio, times(1)).save(captor.capture());
        return captor.getValue();
    }

    // --- Criterio 2: crear la incidencia ---

    /** 3 trenes de la C3 con 10 minutos justos de retraso: se crea una incidencia por retraso en la C3. */
    @Test
    void tresTrenesConDiezMinutos_creaIncidenciaDeRetraso() {
        Map<String, List<RetrasoParada>> retrasos = new HashMap<>();
        anadirTrenes(retrasos, "R_C3", 3, 600);
        red.reemplazarRetrasos(retrasos);

        servicio.detectarIncidenciasPorRetraso(AHORA);

        Incidencia creada = incidenciaGuardada();
        assertEquals("C3", creada.getLinea());
        assertEquals(ServicioIncidencias.TIPO_RETRASO, creada.getTipo());
        assertEquals(OrigenIncidencia.DETECCION_PROPIA, creada.getOrigen());
        assertEquals(AHORA, creada.getInicio());
        assertNull(creada.getIdExterno());
        assertTrue(creada.isActiva());
        assertTrue(creada.getDescripcion().startsWith("3 trenes"));
    }

    /** Solo 2 trenes retrasados en la línea: no llega al mínimo y no se crea nada. */
    @Test
    void soloDosTrenesRetrasados_noCreaIncidencia() {
        Map<String, List<RetrasoParada>> retrasos = new HashMap<>();
        anadirTrenes(retrasos, "R_C3", 2, 900);
        red.reemplazarRetrasos(retrasos);

        servicio.detectarIncidenciasPorRetraso(AHORA);

        verify(repositorio, never()).save(any());
    }

    /** 3 trenes con 9 min 59 s de retraso: no cuentan como retrasados y no se crea nada. */
    @Test
    void retrasoMenorDeDiezMinutos_noCuenta() {
        Map<String, List<RetrasoParada>> retrasos = new HashMap<>();
        anadirTrenes(retrasos, "R_C3", 3, 599);
        red.reemplazarRetrasos(retrasos);

        servicio.detectarIncidenciasPorRetraso(AHORA);

        verify(repositorio, never()).save(any());
    }

    /** 2 trenes retrasados en la C3 y 2 en la C4: no se suman entre líneas distintas, no se crea nada. */
    @Test
    void trenesDeLineasDistintas_noSeMezclan() {
        Map<String, List<RetrasoParada>> retrasos = new HashMap<>();
        anadirTrenes(retrasos, "R_C3", 2, 900);
        anadirTrenes(retrasos, "R_C4", 2, 900);
        red.reemplazarRetrasos(retrasos);

        servicio.detectarIncidenciasPorRetraso(AHORA);

        verify(repositorio, never()).save(any());
    }

    /** Trenes cuyo trip_id no está en los horarios GTFS: la línea se saca del propio trip_id. */
    @Test
    void tripIdNoEstaEnLosHorarios_obtieneLaLineaDelTripId() {
        red.reemplazarRetrasos(Map.of(
                "5001_C5_X", List.of(RetrasoParada.deTodoElViaje(700)),
                "5002_C5_X", List.of(RetrasoParada.deTodoElViaje(700)),
                "5003_C5_X", List.of(RetrasoParada.deTodoElViaje(700))));

        servicio.detectarIncidenciasPorRetraso(AHORA);

        assertEquals("C5", incidenciaGuardada().getLinea());
    }

    // --- Criterio 3: no duplicar ---

    /** La incidencia de la C3 ya existía y se sigue detectando: no se crea otra, se actualiza la existente. */
    @Test
    void incidenciaYaActiva_noSeDuplicaYSeActualiza() {
        Map<String, List<RetrasoParada>> retrasos = new HashMap<>();
        anadirTrenes(retrasos, "R_C3", 4, 900);
        red.reemplazarRetrasos(retrasos);

        LocalDateTime inicioOriginal = AHORA.minusMinutes(5);
        Incidencia existente = new Incidencia("C3", ServicioIncidencias.TIPO_RETRASO, "3 trenes...",
                OrigenIncidencia.DETECCION_PROPIA, null, inicioOriginal);
        when(repositorio.findByLineaAndOrigenAndFinIsNull("C3", OrigenIncidencia.DETECCION_PROPIA))
                .thenReturn(Optional.of(existente));
        when(repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.DETECCION_PROPIA))
                .thenReturn(List.of(existente));

        servicio.detectarIncidenciasPorRetraso(AHORA);

        assertSame(existente, incidenciaGuardada());
        assertEquals(inicioOriginal, existente.getInicio());
        assertEquals(AHORA, existente.getUltimaActualizacion());
        assertTrue(existente.getDescripcion().startsWith("4 trenes"));
        assertTrue(existente.isActiva());
    }

    // --- Criterio 4: cerrar la incidencia ---

    /** La C3 tenía una incidencia activa y ya no hay trenes retrasados: se cierra con la hora de la revisión. */
    @Test
    void dejaDeDetectarse_seCierraConSuHoraDeFin() {
        red.reemplazarRetrasos(Map.of());
        Incidencia activa = new Incidencia("C3", ServicioIncidencias.TIPO_RETRASO, "3 trenes...",
                OrigenIncidencia.DETECCION_PROPIA, null, AHORA.minusMinutes(30));
        when(repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.DETECCION_PROPIA))
                .thenReturn(List.of(activa));

        servicio.detectarIncidenciasPorRetraso(AHORA);

        assertSame(activa, incidenciaGuardada());
        assertFalse(activa.isActiva());
        assertEquals(AHORA, activa.getFin());
    }

    /** La C3 baja a 2 trenes retrasados: ya no cumple la condición y su incidencia se cierra. */
    @Test
    void bajaDelMinimoDeTrenes_seCierra() {
        Map<String, List<RetrasoParada>> retrasos = new HashMap<>();
        anadirTrenes(retrasos, "R_C3", 2, 900);
        red.reemplazarRetrasos(retrasos);
        Incidencia activa = new Incidencia("C3", ServicioIncidencias.TIPO_RETRASO, "3 trenes...",
                OrigenIncidencia.DETECCION_PROPIA, null, AHORA.minusMinutes(30));
        when(repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.DETECCION_PROPIA))
                .thenReturn(List.of(activa));

        servicio.detectarIncidenciasPorRetraso(AHORA);

        assertEquals(AHORA, activa.getFin());
    }

    // --- Fallo de la API ---

    /** Si la última descarga de retrasos ha fallado, no se crea ni se cierra nada. */
    @Test
    void sinDatosDeTiempoReal_noCambiaNada() {
        red.setHorariosTiempoRealActivos(false);
        Map<String, List<RetrasoParada>> retrasos = new HashMap<>();
        anadirTrenes(retrasos, "R_C3", 3, 900);
        red.reemplazarRetrasos(retrasos);

        servicio.detectarIncidenciasPorRetraso(AHORA);

        verifyNoInteractions(repositorio);
    }
}
