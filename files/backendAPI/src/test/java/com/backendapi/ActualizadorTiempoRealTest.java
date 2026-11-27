package com.backendapi;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.backendapi.servicio.ServicioIncidencias;

import loader.ClienteApiRenfe;
import repositorio.RedTransporte;

/**
 * Tests unitarios de ActualizadorTiempoReal: comprueban que, tras cada descarga de Renfe, se lanza la
 * detección de incidencias (US-017). Usa mocks, así que no descarga nada ni necesita base de datos.
 */
class ActualizadorTiempoRealTest {

    private ClienteApiRenfe cliente;
    private RedTransporte red;
    private ServicioIncidencias servicioIncidencias;

    /** Prepara el cliente de Renfe y el servicio de incidencias simulados y una red vacía. */
    @BeforeEach
    void preparar() {
        cliente = mock(ClienteApiRenfe.class);
        red = new RedTransporte();
        servicioIncidencias = mock(ServicioIncidencias.class);
    }

    /** Con la descarga activa: primero se actualizan los datos de Renfe y después se detectan las incidencias. */
    @Test
    void actualizar_activo_descargaYDespuesDetectaIncidencias() {
        ActualizadorTiempoReal actualizador = new ActualizadorTiempoReal(cliente, red, servicioIncidencias, true);

        actualizador.actualizar();

        InOrder orden = inOrder(cliente, servicioIncidencias);
        orden.verify(cliente).actualizarTiempoReal(red);
        orden.verify(servicioIncidencias).detectarIncidenciasPorRetraso(any(LocalDateTime.class));
    }

    /** Con la descarga desactivada (renfe.api.activo=false, como en los tests) no se descarga ni se detecta nada. */
    @Test
    void actualizar_inactivo_noHaceNada() {
        ActualizadorTiempoReal actualizador = new ActualizadorTiempoReal(cliente, red, servicioIncidencias, false);

        actualizador.actualizar();

        verifyNoInteractions(cliente, servicioIncidencias);
    }

    /** Si la detección de incidencias falla, el error no corta la actualización ni llega al programador de tareas. */
    @Test
    void actualizar_errorAlDetectar_noRompeLaActualizacion() {
        doThrow(new RuntimeException("Base de datos no disponible"))
                .when(servicioIncidencias).detectarIncidenciasPorRetraso(any(LocalDateTime.class));
        ActualizadorTiempoReal actualizador = new ActualizadorTiempoReal(cliente, red, servicioIncidencias, true);

        assertDoesNotThrow(actualizador::actualizar);
        verify(cliente).actualizarTiempoReal(red);
    }
}
