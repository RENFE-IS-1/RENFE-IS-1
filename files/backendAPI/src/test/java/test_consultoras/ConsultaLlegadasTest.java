package test_consultoras;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import consultas.ConsultaLlegadas;
import modelo.Estacion;
import modelo.Llegada;
import modelo.Linea;
import modelo.ParadaHorario;
import modelo.ServicioCalendario;
import modelo.Viaje;
import repositorio.RedTransporte;

/** Pruebas para las consultas de llegadas y sentidos de línea. */
class ConsultaLlegadasTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 10, 26);

    /**
     * Crea una red mínima con un viaje entre dos estaciones.
     * Los horarios son segundos desde medianoche, como en los datos GTFS.
     */
    private RedTransporte crearRedConViaje(String tripId, String routeId, String nombreLinea,
            int llegadaOrigen, int llegadaDestino) {
        RedTransporte red = new RedTransporte();
        Estacion origen = new Estacion("origen", "Atocha", 40.0, -3.0);
        Estacion destino = new Estacion("destino", "Chamartín", 40.0, -3.0);
        Linea linea = new Linea(routeId, nombreLinea, "Línea de prueba", "000000");
        Viaje viaje = new Viaje(tripId, routeId, "servicio");

        red.addEstacion(origen);
        red.addEstacion(destino);
        red.addLinea(linea);
        red.addViaje(viaje);
        // Este calendario mantiene activo el servicio todos los días del periodo de prueba.
        boolean[] dias = new boolean[7];
        dias[FECHA.getDayOfWeek().getValue() - 1] = true;
        red.addServicioCalendario(new ServicioCalendario(
                "servicio", dias, FECHA.minusDays(3), FECHA.plusDays(3)));
        // Añadir las dos paradas del viaje. 08:00 y 08:30 se expresan en segundos.
        red.addParadaHorario(new ParadaHorario(tripId, llegadaOrigen, llegadaOrigen,
                "origen", 1));
        red.addParadaHorario(new ParadaHorario(tripId, llegadaDestino, llegadaDestino,
                "destino", 2));
        return red;
    }

    /** PRIMER TEST: verifica que aparece la llegada programada y su destino final. */
    @Test
    void devuelveLlegadaProgramadaConDestino() {
        RedTransporte red = crearRedConViaje("viaje-1", "ruta-1", "C1", 8 * 3600, 8 * 3600 + 1800);
        ConsultaLlegadas consulta = new ConsultaLlegadas(red);
        Estacion origen = red.getEstacion("origen");

        // Consultar desde antes de las 08:00: debe incluir el tren de las 08:00.
        List<Llegada> resultado = consulta.proximasLlegadas(
                origen, FECHA.atTime(7, 59), 10);

        assertEquals(1, resultado.size());
        assertEquals(FECHA.atTime(8, 0), resultado.get(0).hora());
        assertEquals("C1", resultado.get(0).linea().getShortName());
        assertEquals("Chamartín", resultado.get(0).destino().getNombre());
        assertFalse(resultado.get(0).terminaAqui());
    }

    /** SEGUNDO TEST: comprueba que no se devuelven trenes anteriores a la hora solicitada. */
    @Test
    void excluyeLlegadasAnterioresALaHoraSolicitada() {
        RedTransporte red = crearRedConViaje("viaje-1", "ruta-1", "C1", 8 * 3600, 8 * 3600 + 1800);
        ConsultaLlegadas consulta = new ConsultaLlegadas(red);
        Estacion origen = red.getEstacion("origen");

        List<Llegada> resultado = consulta.proximasLlegadas(
                origen, FECHA.atTime(8, 1), 10);

        assertTrue(resultado.isEmpty());
    }

    /** Comprueba que maximo limita cuántas llegadas devuelve. */
    @Test
    void limitaNumeroDeLlegadasAlMaximoSolicitado() {
        RedTransporte red = crearRedConViaje("viaje-1", "ruta-1", "C1", 8 * 3600, 8 * 3600 + 1800);
        agregarViajeAdicional(red, "viaje-2", "ruta-1", "C1", 9 * 3600);
        ConsultaLlegadas consulta = new ConsultaLlegadas(red);

        List<Llegada> resultado = consulta.proximasLlegadas(
                red.getEstacion("origen"), FECHA.atTime(7, 0), 1);

        assertEquals(1, resultado.size());
        assertEquals(FECHA.atTime(8, 0), resultado.get(0).hora());
    }

    /** Comprueba que las llegadas se ordenan cronológicamente. */
    @Test
    void ordenaLlegadasDeLaMasProximaALaMasLejana() {
        RedTransporte red = crearRedConViaje("viaje-1", "ruta-1", "C1", 9 * 3600, 9 * 3600 + 1800);
        agregarViajeAdicional(red, "viaje-2", "ruta-1", "C1", 8 * 3600);
        ConsultaLlegadas consulta = new ConsultaLlegadas(red);

        List<Llegada> resultado = consulta.proximasLlegadas(
                red.getEstacion("origen"), FECHA.atTime(7, 0), 10);

        assertEquals(2, resultado.size());
        assertEquals(FECHA.atTime(8, 0), resultado.get(0).hora());
        assertEquals(FECHA.atTime(9, 0), resultado.get(1).hora());
    }

    /** Comprueba que maximo cero produce una lista vacía. */
    @Test
    void devuelveListaVaciaCuandoElMaximoEsCero() {
        RedTransporte red = crearRedConViaje("viaje-1", "ruta-1", "C1", 8 * 3600, 8 * 3600 + 1800);
        ConsultaLlegadas consulta = new ConsultaLlegadas(red);

        List<Llegada> resultado = consulta.proximasLlegadas(
                red.getEstacion("origen"), FECHA.atTime(7, 0), 0);

        assertTrue(resultado.isEmpty());
    }

    /** Comprueba que una estación nula se maneja devolviendo lista vacía. */
    @Test
    void devuelveListaVaciaCuandoLaEstacionEsNula() {
        RedTransporte red = crearRedConViaje("viaje-1", "ruta-1", "C1", 8 * 3600, 8 * 3600 + 1800);
        ConsultaLlegadas consulta = new ConsultaLlegadas(red);

        List<Llegada> resultado = consulta.proximasLlegadas(null, FECHA.atTime(7, 0), 10);

        assertTrue(resultado.isEmpty());
    }

    /** Comprueba el filtro exacto por línea: C1 no debe incluir C10. */
    @Test
    void filtroDeLineaC1NoIncluyeC10() {
        RedTransporte red = crearRedConViaje("viaje-c1", "ruta-1", "C1", 8 * 3600, 8 * 3600 + 1800);
        agregarViajeAdicional(red, "viaje-c10", "ruta-10", "C10", 8 * 3600 + 600);
        ConsultaLlegadas consulta = new ConsultaLlegadas(red);

        List<Llegada> resultado = consulta.proximasLlegadas(
                red.getEstacion("origen"), FECHA.atTime(7, 0), 10, "C1");

        assertEquals(1, resultado.size());
        assertEquals("C1", resultado.get(0).linea().getShortName());
    }

    /** Comprueba que el filtro comercial C4 también acepta las líneas C4a y C4b. */
    @Test
    void filtroC4IncluyeVariantesC4aYC4b() {
        RedTransporte red = crearRedConViaje("viaje-c4a", "ruta-4a", "C4a", 8 * 3600, 8 * 3600 + 1800);
        agregarViajeAdicional(red, "viaje-c4b", "ruta-4b", "C4b", 8 * 3600 + 600);
        ConsultaLlegadas consulta = new ConsultaLlegadas(red);

        List<Llegada> resultado = consulta.proximasLlegadas(
                red.getEstacion("origen"), FECHA.atTime(7, 0), 10, "C4");

        assertEquals(2, resultado.size());
        assertEquals("C4a", resultado.get(0).linea().getShortName());
        assertEquals("C4b", resultado.get(1).linea().getShortName());
    }

    /** Comprueba que un viaje del día anterior con hora GTFS 24:30 aparece a las 00:30. */
    @Test
    void interpretaHorasGtfsMayoresDe24AlDiaSiguiente() {
        RedTransporte red = crearRedConViaje("viaje-noche", "ruta-noche", "C1",
                24 * 3600 + 30 * 60, 25 * 3600);
        ConsultaLlegadas consulta = new ConsultaLlegadas(red);

        List<Llegada> resultado = consulta.proximasLlegadas(
                red.getEstacion("origen"), FECHA.plusDays(1).atTime(0, 0), 10);

        assertEquals(1, resultado.size());
        assertEquals(FECHA.plusDays(1).atTime(0, 30), resultado.get(0).hora());
    }

    /** Comprueba que fechaCubierta reconoce el intervalo configurado en el calendario. */
    @Test
    void reconoceFechaDentroDelPeriodoDeDatos() {
        RedTransporte red = crearRedConViaje("viaje-1", "ruta-1", "C1", 8 * 3600, 8 * 3600 + 1800);
        ConsultaLlegadas consulta = new ConsultaLlegadas(red);

        assertTrue(consulta.fechaCubierta(FECHA));
        assertFalse(consulta.fechaCubierta(FECHA.minusDays(10)));
    }

    /** Comprueba que lineasYSentidos muestra línea y estación final del viaje. */
    @Test
    void devuelveLineaYSentidoDelViajeActivo() {
        RedTransporte red = crearRedConViaje("viaje-1", "ruta-1", "C1", 8 * 3600, 8 * 3600 + 1800);
        ConsultaLlegadas consulta = new ConsultaLlegadas(red);

        Map<String, Set<String>> resultado = consulta.lineasYSentidos(
                red.getEstacion("origen"), FECHA, null);

        assertEquals(Set.of("Chamartín"), resultado.get("C1"));
    }

    /** Añade un segundo viaje que comparte origen y destino para probar orden y filtros. */
    private void agregarViajeAdicional(RedTransporte red, String tripId, String routeId,
            String nombreLinea, int llegadaOrigen) {
        // Registrar una línea y un viaje adicional con el mismo servicio activo.
        red.addLinea(new Linea(routeId, nombreLinea, "Línea de prueba", "000000"));
        red.addViaje(new Viaje(tripId, routeId, "servicio"));
        // Añadir el paso por Atocha y la llegada final a Chamartín.
        red.addParadaHorario(new ParadaHorario(tripId, llegadaOrigen, llegadaOrigen,
                "origen", 1));
        red.addParadaHorario(new ParadaHorario(tripId, llegadaOrigen + 1800, llegadaOrigen + 1800,
                "destino", 2));
    }
}



