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

        Map<String, Object> res = sinTiempoReal.getTrenesMapa();

        assertEquals(false, res.get("disponible"));
        assertTrue(((List<?>) res.get("trenes")).isEmpty());
    }

    @Test
    void getTrenesMapa_ignoraVehiculosSinCoordenadasOConCoordenadasInvalidas() {
        GtfsRtJsonDto.VehiclePosition sinPos = new GtfsRtJsonDto.VehiclePosition();

        GtfsRtJsonDto.Position posInvalida = new GtfsRtJsonDto.Position();
        posInvalida.setLatitude(95.0);
        posInvalida.setLongitude(-3.7);
        GtfsRtJsonDto.VehiclePosition fueraDeRango = new GtfsRtJsonDto.VehiclePosition();
        fueraDeRango.setPosition(posInvalida);

        when(actualizador.getPosicionesEnVivo()).thenReturn(List.of(sinPos, fueraDeRango));

        Map<String, Object> res = controller.getTrenesMapa();

        assertEquals(true, res.get("disponible"));
        assertTrue(((List<?>) res.get("trenes")).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getTrenesMapa_trenConRetraso_incluyeSegundosDeRetrasoYColorDeLinea() {
        red.reemplazarRetrasos(Map.of("1000_C1_TEST", List.of(RetrasoParada.deTodoElViaje(240))));

        GtfsRtJsonDto.Trip trip = new GtfsRtJsonDto.Trip();
        trip.setTripId("1000_C1_TEST");

        GtfsRtJsonDto.Position pos = new GtfsRtJsonDto.Position();
        pos.setLatitude(40.4393);
        pos.setLongitude(-3.6866);

        GtfsRtJsonDto.VehiclePosition vp = new GtfsRtJsonDto.VehiclePosition();
        vp.setTrip(trip);
        vp.setPosition(pos);

        when(actualizador.getPosicionesEnVivo()).thenReturn(List.of(vp));

        Map<String, Object> res = controller.getTrenesMapa();
        List> trenes = (List>) res.get("trenes");

        assertEquals(1, trenes.size());
        assertEquals("C1", trenes.get(0).get("linea"));
        assertEquals("#75B6E5", trenes.get(0).get("color"));
        assertEquals(240, trenes.get(0).get("retrasoSegundos"));
    }
}