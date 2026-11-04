package servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import modelo.Estacion;
import modelo.Linea;
import modelo.ParadaHorario;
import modelo.ServicioCalendario;
import modelo.Viaje;
import repositorio.RedTransporte;

class ServicioBuscadorRutasTest {

    /** Las búsquedas simultáneas durante la construcción inicial deben observar el índice completo. */
    @Test
    void construyeIndiceDeRutasDeFormaSeguraParaLlamadasConcurrentes() throws Exception {
        RedTransporte red = crearRedDirecta();
        ServicioBuscadorRutas buscador = new ServicioBuscadorRutas(red);
        int cantidad = 32;
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService ejecutores = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> resultados = new ArrayList<>();
            for (int i = 0; i < cantidad; i++) {
                resultados.add(ejecutores.submit(() -> {
                    salida.await();
                    return buscador.buscarRutas(red.getEstacion("A"), red.getEstacion("B"),
                            LocalDateTime.of(2026, 10, 26, 7, 0)).size();
                }));
            }
            salida.countDown();
            for (Future<Integer> resultado : resultados) {
                assertEquals(1, resultado.get(3, TimeUnit.SECONDS));
            }
        } finally {
            ejecutores.shutdownNow();
        }
    }

    /** La misma estación como origen y destino no produce una ruta vacía engañosa. */
    @Test
    void devuelveListaVaciaCuandoOrigenYDestinoSonLaMismaEstacion() {
        RedTransporte red = crearRedDirecta();
        ServicioBuscadorRutas buscador = new ServicioBuscadorRutas(red);

        assertTrue(buscador.buscarRutas(red.getEstacion("A"), red.getEstacion("A"),
                LocalDateTime.of(2026, 10, 26, 7, 0)).isEmpty());
    }

    /** Una ruta con dos líneas debe respetar el orden de los trenes y completar el transbordo. */
    @Test
    void encuentraRutaDeDosTrenesConTransbordoProgramado() {
        RedTransporte red = new RedTransporte();
        red.addEstacion(new Estacion("A", "Origen", 40.0, -3.0));
        red.addEstacion(new Estacion("B", "Intercambio", 40.1, -3.1));
        red.addEstacion(new Estacion("C", "Destino", 40.2, -3.2));
        red.addLinea(new Linea("1001", "C1", "Primera", "000000"));
        red.addLinea(new Linea("1002", "C2", "Segunda", "000000"));
        red.addViaje(new Viaje("T1", "1001", "SVC"));
        red.addViaje(new Viaje("T2", "1002", "SVC"));
        LocalDate lunes = LocalDate.of(2026, 10, 26);
        boolean[] dias = new boolean[7];
        dias[0] = true;
        red.addServicioCalendario(new ServicioCalendario("SVC", dias, lunes, lunes));
        red.addParadaHorario(new ParadaHorario("T1", 8 * 3600, 8 * 3600, "A", 1));
        red.addParadaHorario(new ParadaHorario("T1", 8 * 3600 + 20 * 60, 8 * 3600 + 20 * 60, "B", 2));
        red.addParadaHorario(new ParadaHorario("T2", 8 * 3600 + 25 * 60, 8 * 3600 + 25 * 60, "B", 1));
        red.addParadaHorario(new ParadaHorario("T2", 8 * 3600 + 45 * 60, 8 * 3600 + 45 * 60, "C", 2));

        var rutas = new ServicioBuscadorRutas(red).buscarRutas(red.getEstacion("A"), red.getEstacion("C"),
                LocalDateTime.of(2026, 10, 26, 7, 0));

        assertEquals(1, rutas.size());
        assertEquals(2, rutas.get(0).getTramos().size());
        assertEquals("C1", rutas.get(0).getTramos().get(0).getLinea().getShortName());
        assertEquals("C2", rutas.get(0).getTramos().get(1).getLinea().getShortName());
        assertEquals(LocalDateTime.of(2026, 10, 26, 8, 45), rutas.get(0).getFechaHoraLlegada());
    }

    /** Si el calendario no ofrece viajes en esa fecha, una ruta física no basta para ofrecer servicio. */
    @Test
    void noOfreceRutaCuandoElServicioNoCirculaEseDia() {
        RedTransporte red = crearRedDirecta();
        ServicioBuscadorRutas buscador = new ServicioBuscadorRutas(red);

        assertTrue(buscador.buscarRutas(red.getEstacion("A"), red.getEstacion("B"),
                LocalDateTime.of(2026, 10, 27, 7, 0)).isEmpty());
    }
    private RedTransporte crearRedDirecta() {
        RedTransporte red = new RedTransporte();
        red.addEstacion(new Estacion("A", "Origen", 40.0, -3.0));
        red.addEstacion(new Estacion("B", "Destino", 40.5, -3.7));
        red.addLinea(new Linea("1001", "C1", "Prueba", "000000"));
        red.addViaje(new Viaje("T1", "1001", "SVC"));
        boolean[] dias = new boolean[7];
        dias[0] = true;
        LocalDate lunes = LocalDate.of(2026, 10, 26);
        red.addServicioCalendario(new ServicioCalendario("SVC", dias, lunes, lunes));
        red.addParadaHorario(new ParadaHorario("T1", 8 * 3600, 8 * 3600, "A", 1));
        red.addParadaHorario(new ParadaHorario("T1", 8 * 3600 + 1800, 8 * 3600 + 1800, "B", 2));
        return red;
    }
}
