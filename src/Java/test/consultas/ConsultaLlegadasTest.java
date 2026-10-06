package consultas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.Test;

import loader.GTFSLoader;
import modelo.Estacion;
import modelo.Llegada;
import repositorio.RedTransporte;

/**
 * Pruebas de US-004 sobre un GTFS de prueba pequeño (test/recursos/gtfs_prueba/).
 *
 * Calendario del GTFS de prueba:
 *  - LUNES: lunes del 05/10/2026 al 26/10/2026, salvo el 12/10/2026 (excepción en calendar_dates).
 *  - DOMINGO: domingos del 04/10/2026 al 25/10/2026.
 *  - EXTRA: solo el miércoles 07/10/2026 (definido únicamente en calendar_dates).
 * Viajes: T1 Árbol→Cumbre (C3), T2 Cumbre→Árbol (C3), T3 Bosque→Bosquecillo (C4a, domingos),
 * T4 Árbol→Cumbre (C3) que sale a las 23:50 y llega a Bosque a las 24:05, T5 Bosque→Cumbre (C10),
 * T6 Bosque→Bosquecillo (C4a, servicio EXTRA).
 */
public class ConsultaLlegadasTest {
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate MARTES = LocalDate.of(2026, 10, 6);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 7);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 11);
    private static final LocalDate FESTIVO = LocalDate.of(2026, 10, 12);

    private static RedTransporte red;
    private static ConsultaLlegadas consulta;
    private static Estacion arbol, bosque, cumbre;

    @BeforeClass
    public static void cargarDatos() {
        red = new RedTransporte();
        new GTFSLoader().cargarDesdeDirectorio("test/recursos/gtfs_prueba/", red);
        consulta = new ConsultaLlegadas(red);
        arbol = red.getEstacion("1");
        bosque = red.getEstacion("2");
        cumbre = red.getEstacion("3");
    }

    private static List<String> viajes(List<Llegada> llegadas) {
        return llegadas.stream().map(Llegada::tripId).collect(Collectors.toList());
    }

    @Test
    public void devuelveLasLlegadasOrdenadasAPartirDeLaHora() {
        List<Llegada> r = consulta.proximasLlegadas(bosque, LUNES.atTime(8, 11), 10);
        assertEquals(List.of("T5", "T2", "T4"), viajes(r)); // T1 (08:10) ya ha pasado
        assertEquals(LUNES.atTime(8, 12), r.get(0).hora());
    }

    @Test
    public void incluyeUnTrenQueLlegaJustoALaHoraConsultada() {
        List<Llegada> r = consulta.proximasLlegadas(bosque, LUNES.atTime(8, 10), 1);
        assertEquals(List.of("T1"), viajes(r));
    }

    @Test
    public void respetaElNumeroMaximo() {
        assertEquals(2, consulta.proximasLlegadas(bosque, LUNES.atTime(0, 0), 2).size());
    }

    @Test
    public void indicaLineaYDestino() {
        Llegada t2 = consulta.proximasLlegadas(bosque, LUNES.atTime(8, 15), 1).get(0);
        assertEquals("T2", t2.tripId());
        assertEquals("C3", t2.linea().getShortName());
        assertEquals("Estación Árbol", t2.destino().getNombre());
        assertFalse(t2.terminaAqui());
    }

    @Test
    public void marcaLosTrenesQueTerminanEnLaEstacion() {
        List<Llegada> r = consulta.proximasLlegadas(cumbre, LUNES.atTime(8, 0), 10);
        Llegada t2 = r.stream().filter(l -> l.tripId().equals("T2")).findFirst().get();
        Llegada t1 = r.stream().filter(l -> l.tripId().equals("T1")).findFirst().get();
        assertFalse(t2.terminaAqui()); // T2 sale de Cumbre
        assertTrue(t1.terminaAqui());  // T1 termina en Cumbre
    }

    @Test
    public void soloIncluyeServiciosQueCirculanEseDia() {
        assertEquals(List.of("T3"), viajes(consulta.proximasLlegadas(bosque, DOMINGO.atTime(0, 0), 10)));
        assertTrue(viajes(consulta.proximasLlegadas(bosque, LUNES.atTime(0, 0), 10)).stream()
                .noneMatch(t -> t.equals("T3") || t.equals("T6")));
    }

    @Test
    public void aplicaLasExcepcionesDeCalendarDates() {
        // El 12/10 es lunes pero el servicio LUNES está suprimido
        assertTrue(consulta.proximasLlegadas(bosque, FESTIVO.atTime(0, 0), 10).isEmpty());
        // El servicio EXTRA solo existe por calendar_dates
        assertEquals(List.of("T6"), viajes(consulta.proximasLlegadas(bosque, MIERCOLES.atTime(0, 0), 10)));
    }

    @Test
    public void trataLasHorasPosterioresALas24() {
        // T4 es del día de servicio del lunes pero pasa por Bosque a las 24:05 = martes 00:05
        // (después vendría T6, que es del miércoles: la consulta también mira el día siguiente)
        List<Llegada> r = consulta.proximasLlegadas(bosque, MARTES.atTime(0, 0), 10);
        assertEquals(List.of("T4", "T6"), viajes(r));
        assertEquals(MARTES.atTime(0, 5), r.get(0).hora());
        assertEquals(MIERCOLES.atTime(10, 0), r.get(1).hora());

        // Consultando el lunes por la noche también aparece, con fecha del martes
        Llegada desdeLunes = consulta.proximasLlegadas(bosque, LUNES.atTime(23, 55), 1).get(0);
        assertEquals("T4", desdeLunes.tripId());
        assertEquals(LocalDateTime.of(MARTES, desdeLunes.hora().toLocalTime()), desdeLunes.hora());
    }

    @Test
    public void filtraPorLinea() {
        assertEquals(List.of("T1", "T2", "T4"), viajes(consulta.proximasLlegadas(bosque, LUNES.atTime(0, 0), 10, "c3")));
        // "C1" no debe confundirse con "C10"
        assertTrue(consulta.proximasLlegadas(bosque, LUNES.atTime(0, 0), 10, "C1").isEmpty());
        assertEquals(List.of("T5"), viajes(consulta.proximasLlegadas(bosque, LUNES.atTime(0, 0), 10, "C10")));
        // "C4" incluye los ramales C4a y C4b
        assertEquals(List.of("T3"), viajes(consulta.proximasLlegadas(bosque, DOMINGO.atTime(0, 0), 10, "C4")));
    }

    @Test
    public void listaLasLineasYSentidosDelDia() {
        Map<String, Set<String>> lineas = consulta.lineasYSentidos(bosque, LUNES, null);
        assertEquals(List.of("C3", "C10"), List.copyOf(lineas.keySet())); // orden comercial, no alfabético
        assertEquals(Set.of("Estación Árbol", "Estación Cumbre"), lineas.get("C3"));
        assertEquals(Set.of("Estación Cumbre"), lineas.get("C10"));

        assertEquals(Set.of("C4a"), consulta.lineasYSentidos(bosque, DOMINGO, null).keySet());
        assertTrue(consulta.lineasYSentidos(bosque, FESTIVO, null).isEmpty());
    }

    @Test
    public void unaLineaQueSoloTerminaEnLaEstacionNoTieneSentidos() {
        Map<String, Set<String>> lineas = consulta.lineasYSentidos(red.getEstacion("4"), DOMINGO, null);
        assertTrue(lineas.get("C4a").isEmpty());
    }

    @Test
    public void conoceElPeriodoCubiertoPorLosDatos() {
        assertTrue(consulta.fechaCubierta(LocalDate.of(2026, 10, 4)));
        assertTrue(consulta.fechaCubierta(LocalDate.of(2026, 10, 26)));
        assertFalse(consulta.fechaCubierta(LocalDate.of(2026, 10, 27)));
        assertFalse(consulta.fechaCubierta(LocalDate.of(2026, 10, 3)));
    }

    @Test
    public void sinEstacionNoDevuelveNada() {
        assertTrue(consulta.proximasLlegadas(null, LUNES.atTime(8, 0), 10).isEmpty());
        assertTrue(consulta.lineasYSentidos(null, LUNES, null).isEmpty());
    }
}
