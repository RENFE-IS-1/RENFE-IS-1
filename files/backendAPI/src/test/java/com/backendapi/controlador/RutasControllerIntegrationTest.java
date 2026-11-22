package com.backendapi.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import consultas.BuscadorEstaciones;
import consultas.ConsultaLlegadas;
import modelo.Estacion;
import modelo.Ruta;
import modelo.Linea;
import modelo.ParadaHorario;
import modelo.ServicioCalendario;
import modelo.Viaje;
import repositorio.RedTransporte;
import servicio.ServicioBuscadorRutas;

/**
 * Pruebas de integración de la capa web: mandan peticiones HTTP al controlador real,
 * que trabaja con una red y un buscador reales. Un servicio de prueba captura los datos
 * que el controlador le pasa, sin calcular rutas reales en estos casos.
 */
class RutasControllerIntegrationTest {

    private MockMvc http;
    private RedTransporte red;
    private ServicioRutasDePrueba servicioRutas;
    private Estacion atocha;
    private Estacion chamartin;

    /** Antes de cada prueba crea una aplicación pequeña y reproducible. */
    @BeforeEach
    void prepararAplicacionDePrueba() {
        // PREPARAR: red ficticia en memoria con estaciones para las peticiones.
        red = new RedTransporte();
        atocha = new Estacion("A", "Atocha", 40.0, -3.0);
        chamartin = new Estacion("C", "Chamartín", 40.5, -3.7);
        red.addEstacion(atocha);
        red.addEstacion(chamartin);
        red.addEstacion(new Estacion("S1", "San Blas", 40.4, -3.6));
        red.addEstacion(new Estacion("S2", "San Fernando", 40.3, -3.5));

        // Añadir un viaje pequeño para que el endpoint real de llegadas tenga datos que consultar.
        red.addLinea(new Linea("R1", "C1", "Línea de prueba", "000000"));
        red.addViaje(new Viaje("T1", "R1", "SERVICIO"));
        boolean[] diasActivos = new boolean[7];
        LocalDate fechaPrueba = LocalDate.of(2026, 10, 26); // lunes
        diasActivos[fechaPrueba.getDayOfWeek().getValue() - 1] = true;
        red.addServicioCalendario(new ServicioCalendario(
                "SERVICIO", diasActivos, fechaPrueba, fechaPrueba));
        red.addParadaHorario(new ParadaHorario("T1", 8 * 3600, 8 * 3600, "A", 1));
        red.addParadaHorario(new ParadaHorario("T1", 8 * 3600 + 1800, 8 * 3600 + 1800, "C", 2));

        // Usamos un servicio de prueba para registrar la entrada que recibe el controlador.
        servicioRutas = new ServicioRutasDePrueba(red);

        // Conectar el controlador real con el repositorio, buscador y consulta de llegadas reales.
        RutasController controlador = new RutasController(
                red,
                new BuscadorEstaciones(red),
                new ConsultaLlegadas(red),
                servicioRutas);

        // Crear un servidor HTTP simulado en memoria; no abre ningún puerto de red.
        http = MockMvcBuilders.standaloneSetup(controlador).build();
    }

    /** El endpoint busca estaciones con el buscador real e ignora las mayúsculas. */
    @Test
    void endpointEstacionesBuscaIgnorandoMayusculas() throws Exception {
        http.perform(get("/api/estaciones").param("nombre", "ATOCHA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Atocha"));
    }

    /** La petición HTTP consulta el horario real de prueba y serializa los datos de llegada. */
    @Test
    void endpointLlegadasDevuelveTrenProgramado() throws Exception {
        http.perform(get("/api/estaciones/A/llegadas")
                .param("desde", "2026-10-26T07:59:00")
                .param("max", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].linea").value("C1"))
                .andExpect(jsonPath("$[0].destino").value("Chamartín"))
                .andExpect(jsonPath("$[0].horaPlanificada").value("08:00:00"))
                .andExpect(jsonPath("$[0].horaEstimada").value("08:00:00"))
                .andExpect(jsonPath("$[0].estado").value("EN_HORA"))
                .andExpect(jsonPath("$[0].esTiempoReal").value(false));
    }

    /** Si no hay coincidencias, la API responde 200 con una lista JSON vacía. */
    @Test
    void endpointEstacionesDevuelveListaVaciaSiNoHayCoincidencias() throws Exception {
        http.perform(get("/api/estaciones").param("nombre", "Barcelona"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    /** El controlador no debe escoger arbitrariamente entre dos estaciones llamadas San... */
    @Test
    void endpointRutasRespondeBadRequestParaOrigenAmbiguo() throws Exception {
        http.perform(get("/api/rutas")
                .param("origen", "San")
                .param("destino", "Atocha")
                .param("salida", "2026-10-26T07:00:00"))
                .andExpect(status().isBadRequest());
        assertEquals(null, servicioRutas.ultimoOrigen);
    }

    /** Si no existe el origen, la API responde 404 y no intenta buscar la ruta. */
    @Test
    void endpointRutasRespondeNotFoundParaEstacionInexistente() throws Exception {
        http.perform(get("/api/rutas")
                .param("origen", "Barcelona")
                .param("destino", "Atocha")
                .param("salida", "2026-10-26T07:00:00"))
                .andExpect(status().isNotFound());
        assertEquals(null, servicioRutas.ultimoOrigen);
    }

    /** La petición HTTP debe resolver nombres y fecha y pasarlos al servicio de rutas. */
    @Test
    void endpointRutasPasaEstacionesYHoraAlServicio() throws Exception {
        LocalDateTime salida = LocalDateTime.of(2026, 10, 26, 7, 0);

        http.perform(get("/api/rutas")
                .param("origen", "ATOCHA")
                .param("destino", "chamartin")
                .param("salida", "2026-10-26T07:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        assertEquals(atocha, servicioRutas.ultimoOrigen);
        assertEquals(chamartin, servicioRutas.ultimoDestino);
        assertEquals(salida, servicioRutas.ultimaSalida);
    }

    /** La petición HTTP recorre también el algoritmo real y devuelve una ruta con trenes de prueba. */
    @Test
    void endpointRutasEncuentraUnTrenDirectoDePrincipioAFin() throws Exception {
        ServicioBuscadorRutas buscadorReal = new ServicioBuscadorRutas(red);
        RutasController controladorReal = new RutasController(
                red,
                new BuscadorEstaciones(red),
                new ConsultaLlegadas(red),
                buscadorReal);
        MockMvc httpConBuscadorReal = MockMvcBuilders.standaloneSetup(controladorReal).build();

        httpConBuscadorReal.perform(get("/api/rutas")
                .param("origen", "Atocha")
                .param("destino", "Chamartín")
                .param("salida", "2026-10-26T07:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tramos.length()").value(1))
                .andExpect(jsonPath("$[0].tramos[0].linea.shortName").value("C1"));
    }
    /** Una fecha mal formada debe producir HTTP 400 antes de buscar la ruta. */
    @Test
    void endpointRutasRechazaFechaConFormatoIncorrecto() throws Exception {
        http.perform(get("/api/rutas")
                .param("origen", "Atocha")
                .param("destino", "Chamartín")
                .param("salida", "mañana"))
                .andExpect(status().isBadRequest());
        assertEquals(null, servicioRutas.ultimoOrigen);
    }

    /** Una línea que no existe produce HTTP 404. */
    @Test
    void endpointLineaInexistenteRespondeNotFound() throws Exception {
        http.perform(get("/api/lineas/C99/estaciones"))
                .andExpect(status().isNotFound());
    }
    
    /** Sin actualizador en tiempo real, /api/trenes/mapa responde 200 con lista vacía. */
    @Test
    void endpointTrenesMapaSinActualizadorDevuelveListaVacia() throws Exception {
        http.perform(get("/api/trenes/mapa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.trenes").isEmpty());
    }

    /** Con posiciones GPS activas, /api/trenes/mapa sitúa cada tren en el tramo y calcula su progreso. */
    @Test
    void endpointTrenesMapaSituaTrenEnTramoConCoordenadasGps() throws Exception {
        com.backendapi.ActualizadorTiempoReal actualizadorMock =
                org.mockito.Mockito.mock(com.backendapi.ActualizadorTiempoReal.class);

        modelo.GtfsRtJsonDto.Trip trip = new modelo.GtfsRtJsonDto.Trip();
        trip.setTripId("1000_C1_TEST");

        modelo.GtfsRtJsonDto.Position pos = new modelo.GtfsRtJsonDto.Position();
        pos.setLatitude(40.25);   // Punto intermedio entre Atocha (40.0) y Chamartín (40.5)
        pos.setLongitude(-3.35);

        modelo.GtfsRtJsonDto.VehiclePosition vp = new modelo.GtfsRtJsonDto.VehiclePosition();
        vp.setTrip(trip);
        vp.setPosition(pos);

        org.mockito.Mockito.when(actualizadorMock.getPosicionesEnVivo()).thenReturn(List.of(vp));

        RutasController controladorConMapa = new RutasController(
                red,
                new BuscadorEstaciones(red),
                new ConsultaLlegadas(red),
                servicioRutas,
                actualizadorMock);
        MockMvc httpConMapa = MockMvcBuilders.standaloneSetup(controladorConMapa).build();

        httpConMapa.perform(get("/api/trenes/mapa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.trenes.length()").value(1))
                .andExpect(jsonPath("$.trenes[0].linea").value("C1"))
                .andExpect(jsonPath("$.trenes[0].desde").value("A"))
                .andExpect(jsonPath("$.trenes[0].hasta").value("C"));
    }

    /** Implementación de prueba que guarda las entradas y responde con cero rutas. */
    private static class ServicioRutasDePrueba extends ServicioBuscadorRutas {
        private Estacion ultimoOrigen;
        private Estacion ultimoDestino;
        private LocalDateTime ultimaSalida;

        private ServicioRutasDePrueba(RedTransporte red) {
            super(red);
        }

        @Override
        public List<Ruta> buscarRutas(Estacion origen, Estacion destino, LocalDateTime salida) {
            ultimoOrigen = origen;
            ultimoDestino = destino;
            ultimaSalida = salida;
            return List.of();
        }
    }
}



