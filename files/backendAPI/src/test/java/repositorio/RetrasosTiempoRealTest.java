package repositorio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import modelo.Estacion;
import modelo.ParadaHorario;
import modelo.RetrasoParada;
import modelo.Viaje;

/**
 * Retrasos en tiempo real por parada: cada parada usa su propio dato o, si no lo tiene, el de la
 * parada anterior más cercana que sí lo tenga (como indica GTFS-RT).
 */
class RetrasosTiempoRealTest {

    private RedTransporte red;

    @BeforeEach
    void preparar() {
        red = new RedTransporte();
        red.addViaje(new Viaje("T1", "R", "S"));
        String[] paradas = {"GUA", "ALC", "TOR", "ATO", "CHA"};
        for (String p : paradas) red.addEstacion(new Estacion(p, p, 40.0, -3.0));
        for (int i = 0; i < paradas.length; i++) {
            red.addParadaHorario(new ParadaHorario("T1", 1000 * (i + 1), 1000 * (i + 1), paradas[i], i + 1));
        }
    }

    @Test
    void cadaParadaUsaSuRetrasoOElDeLaAnterior() {
        // Un tren que recupera tiempo: +5 min en Alcalá, +4 en Torrejón, +2 en Chamartín
        red.reemplazarRetrasos(Map.of("T1", List.of(
                new RetrasoParada("ALC", 2, 300), new RetrasoParada("TOR", 3, 240), new RetrasoParada("CHA", 5, 120))));

        assertEquals(0, red.getRetrasoEnParada("T1", "GUA"));    // antes de la primera actualización
        assertEquals(300, red.getRetrasoEnParada("T1", "ALC"));
        assertEquals(240, red.getRetrasoEnParada("T1", "ATO"));  // sin dato propio: el de Torrejón
        assertEquals(120, red.getRetrasoEnParada("T1", "CHA"));
        assertEquals(300, red.getRetrasoViajeSegundos("T1"));
    }

    @Test
    void sinOrdenDeParadaYConNoData() {
        red.reemplazarRetrasos(Map.of("T1", List.of(
                new RetrasoParada("GUA", null, 60), new RetrasoParada("TOR", null, null))));

        assertEquals(60, red.getRetrasoEnParada("T1", "ALC"));
        assertEquals(0, red.getRetrasoEnParada("T1", "CHA"));    // NO_DATA en Torrejón corta el arrastre
    }

    @Test
    void retrasoDeTodoElViajeYDatosDesconocidos() {
        red.reemplazarRetrasos(Map.of("T1", List.of(RetrasoParada.deTodoElViaje(180))));

        assertEquals(180, red.getRetrasoEnParada("T1", "GUA"));
        assertEquals(180, red.getRetrasoEnParada("T1", "CHA"));
        assertEquals(0, red.getRetrasoEnParada("OTRO", "GUA"));
        assertEquals(0, red.getRetrasoEnParada("T1", "XXX"));
    }
}
