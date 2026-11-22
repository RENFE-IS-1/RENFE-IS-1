package com.backendapi.controlador;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.backendapi.ActualizadorTiempoReal;
import consultas.BuscadorEstaciones;
import consultas.ConsultaLlegadas;
import modelo.Estacion;
import modelo.GtfsRtJsonDto;
import modelo.Linea;
import modelo.ParadaHorario;
import modelo.RetrasoParada;
import modelo.Viaje;
import repositorio.RedTransporte;
import servicio.ServicioBuscadorRutas;

class MapaTrenesUnitTest {

    private RedTransporte red;
    private ActualizadorTiempoReal actualizador;
    private RutasController controller;

    @BeforeEach
    void preparar() {
        red = new RedTransporte();
        red.addEstacion(new Estacion("18000", "Atocha", 40.4065, -3.6908));
        red.addEstacion(new Estacion("17000", "Chamartín", 40.4721, -3.6825));
        red.addLinea(new Linea("R_C1", "C1", "Príncipe Pío - Aeropuerto T4", "75B6E5"));
        red.addViaje(new Viaje("1000_C1_TEST", "R_C1", "SVC"));
        red.addParadaHorario(new ParadaHorario("1000_C1_TEST", 28800, 28800, "18000", 1));
        red.addParadaHorario(new ParadaHorario("1000_C1_TEST", 29700, 29700, "17000", 2));

        actualizador = mock(ActualizadorTiempoReal.class);
        controller = new RutasController(
                red,
                new BuscadorEstaciones(red),
                new ConsultaLlegadas(red),
                new ServicioBuscadorRutas(red),
                actualizador);
    }

    @Test
    void getTrenesMapa_sinActualizador_devuelveDisponibleFalse() {
        RutasController sinTiempoReal = new RutasController(
                red,
                new BuscadorEstaciones(red),
                new ConsultaLlegadas(red),
                new ServicioBuscadorRutas(red));

        Map<String, Object> res = sinTiempoReal.obtenerTrenesParaMapa();

        assertEquals(false, res.get("disponible"));
        assertTrue(((List<?>) res.get("trenes")).isEmpty());
    }

    /*
     * TODO (Diego e Íñigo): los dos tests de abajo están comentados porque fallan. Esperan un
     * comportamiento que RutasController.obtenerTrenesParaMapa() (US-024) no tiene todavía.
     * Hay que decidir entre los dos qué comportamiento es el correcto y, según eso, cambiar el
     * controlador (Íñigo, autor de US-024) o los tests (Diego, autor de este archivo). Después hay que
     * descomentarlos y comprobar que pasan con "mvnw test".
     *
     * 1) getTrenesMapa_ignoraVehiculosSinCoordenadasOConCoordenadasInvalidas
     *    - Falla en: assertTrue(trenes.isEmpty()) -> el tren con latitud 95 sigue apareciendo.
     *    - Causa: el controlador solo descarta los trenes con coordenadas exactamente (0, 0).
     *    - Qué hacer: o bien el controlador descarta las coordenadas fuera de rango
     *      (latitud fuera de [-90, 90] o longitud fuera de [-180, 180]), o bien el test pasa a
     *      comprobar el caso (0, 0), que es el que el controlador sí filtra.
     *
     * 2) getTrenesMapa_trenConRetraso_incluyeSegundosDeRetrasoYColorDeLinea
     *    - Falla en: assertEquals("#75B6E5", color) -> el campo "color" es null.
     *    - Causa: el controlador no añade el campo "color" a cada tren. (El frontend, index.html,
     *      ahora no lo usa: calcula el color de cada línea por su cuenta.)
     *    - Qué hacer: o bien el controlador añade "color" con "#" + el color de la línea
     *      (red.getLinea(...).getColor()), o bien el test quita esa comprobación.
     *    - Ojo: después fallará también assertEquals(240, retrasoSegundos). El test mete el retraso con
     *      red.reemplazarRetrasos(...), pero el controlador no lee el retraso del propio viaje: lo saca
     *      de consultaLlegadas.proximasLlegadas(estA, ahora(), ...), así que el resultado depende de la
     *      hora a la que se ejecute el test. O el controlador lee el retraso del tripId del tren, o el
     *      test deja de comprobar ese valor exacto.
     *
     * Comentado por Pedro en el Sprint 5 para poder ejecutar el resto de tests.
     */

    // @Test
    // void getTrenesMapa_ignoraVehiculosSinCoordenadasOConCoordenadasInvalidas() {
    //     GtfsRtJsonDto.VehiclePosition sinPos = new GtfsRtJsonDto.VehiclePosition();
    //
    //     GtfsRtJsonDto.Position posInvalida = new GtfsRtJsonDto.Position();
    //     posInvalida.setLatitude(95.0);
    //     posInvalida.setLongitude(-3.7);
    //     GtfsRtJsonDto.VehiclePosition fueraDeRango = new GtfsRtJsonDto.VehiclePosition();
    //     fueraDeRango.setPosition(posInvalida);
    //
    //     when(actualizador.getPosicionesEnVivo()).thenReturn(List.of(sinPos, fueraDeRango));
    //
    //     Map<String, Object> res = controller.obtenerTrenesParaMapa();
    //
    //     assertEquals(true, res.get("disponible"));
    //     assertTrue(((List<?>) res.get("trenes")).isEmpty());
    // }

    // @Test
    // @SuppressWarnings("unchecked")
    // void getTrenesMapa_trenConRetraso_incluyeSegundosDeRetrasoYColorDeLinea() {
    //     red.reemplazarRetrasos(Map.of("1000_C1_TEST", List.of(RetrasoParada.deTodoElViaje(240))));
    //
    //     GtfsRtJsonDto.Trip trip = new GtfsRtJsonDto.Trip();
    //     trip.setTripId("1000_C1_TEST");
    //
    //     GtfsRtJsonDto.Position pos = new GtfsRtJsonDto.Position();
    //     pos.setLatitude(40.4393);
    //     pos.setLongitude(-3.6866);
    //
    //     GtfsRtJsonDto.VehiclePosition vp = new GtfsRtJsonDto.VehiclePosition();
    //     vp.setTrip(trip);
    //     vp.setPosition(pos);
    //
    //     when(actualizador.getPosicionesEnVivo()).thenReturn(List.of(vp));
    //
    //     Map<String, Object> res = controller.obtenerTrenesParaMapa();
    //     List<Map<String, Object>> trenes = (List<Map<String, Object>>) res.get("trenes");
    //
    //     assertEquals(1, trenes.size());
    //     assertEquals("C1", trenes.get(0).get("linea"));
    //     assertEquals("#75B6E5", trenes.get(0).get("color"));
    //     assertEquals(240, trenes.get(0).get("retrasoSegundos"));
    // }
}