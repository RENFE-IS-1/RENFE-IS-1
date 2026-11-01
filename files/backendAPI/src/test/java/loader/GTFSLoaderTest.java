package loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import modelo.ParadaHorario;
import repositorio.RedTransporte;

class GTFSLoaderTest {

    @TempDir
    Path directorioTemporal;

    /** Comprueba que cargar el directorio no exige que la ruta termine con una barra. */
    @Test
    void cargaArchivosCuandoLaRutaNoTerminaEnSeparador() throws IOException {
        prepararArchivosGtfs(false, false);
        RedTransporte red = new RedTransporte();

        new GTFSLoader().cargarDesdeDirectorio(directorioTemporal.toString(), red);

        assertNotNull(red.getEstacion("A"));
        assertEquals(2, red.getRutaDeViaje("T1").size());
        assertEquals("123ABC", red.getLinea("1001").getColor());
    }

    /** Un nombre GTFS con coma entrecomillada debe conservarse completo, sin desplazar latitud y longitud. */
    @Test
    void conservaComasDentroDeCamposEntrecomillados() throws IOException {
        prepararArchivosGtfs(true, false);
        RedTransporte red = new RedTransporte();

        new GTFSLoader().cargarDesdeDirectorio(directorioTemporal.toString(), red);

        assertEquals("Central, Chamartín", red.getEstacion("A").getNombre());
        assertEquals(40.0, red.getEstacion("A").getLatitud(), 0.0001);
        assertEquals(-3.0, red.getEstacion("A").getLongitud(), 0.0001);
    }

    /** GTFS permite horas superiores a 24 para viajes que continúan después de medianoche. */
    @Test
    void conservaHorariosGtfsPosterioresALas24Horas() throws IOException {
        prepararArchivosGtfs(false, false);
        escribir("stop_times.txt", "trip_id,arrival_time,departure_time,stop_id,stop_sequence\nT1,25:15:00,25:15:00,A,1\nT1,25:45:00,25:45:00,B,2\n");
        RedTransporte red = new RedTransporte();

        new GTFSLoader().cargarDesdeDirectorio(directorioTemporal.toString(), red);

        assertEquals(25 * 3600 + 15 * 60, red.getHorariosEstacion("A").get(0).getArrivalTime());
    }

    /** La excepción GTFS tipo 2 elimina un servicio que el calendario semanal marcaría activo. */
    @Test
    void aplicaExcepcionQueEliminaUnServicioEnUnaFecha() throws IOException {
        prepararArchivosGtfs(false, true);
        RedTransporte red = new RedTransporte();

        new GTFSLoader().cargarDesdeDirectorio(directorioTemporal.toString(), red);

        assertFalse(red.isServicioActivo("SVC", LocalDate.of(2026, 10, 26)));
    }

    /** La excepción tipo 1 puede activar un servicio en una fecha que no está habilitada semanalmente. */
    @Test
    void aplicaExcepcionQueAnadeUnServicioEnUnaFecha() throws IOException {
        prepararArchivosGtfs(false, false);
        agregar("calendar_dates.txt", "service_id,date,exception_type\nSVC,20261027,1\n");
        RedTransporte red = new RedTransporte();

        new GTFSLoader().cargarDesdeDirectorio(directorioTemporal.toString(), red);

        assertTrue(red.isServicioActivo("SVC", LocalDate.of(2026, 10, 27)));
    }

    private void prepararArchivosGtfs(boolean nombreConComa, boolean eliminarServicio) throws IOException {
        String nombre = nombreConComa ? "\"Central, Chamartín\"" : "Central";
        escribir("stops.txt", "stop_id,stop_name,stop_lat,stop_lon\nA," + nombre + ",40.0,-3.0\nB,Terminal,40.5,-3.7\n");
        escribir("routes.txt", "route_id,agency_id,route_short_name,route_long_name,route_type,route_color,route_text_color\n1001,ag,C1,Prueba,2,123ABC,FFFFFF\n");
        escribir("trips.txt", "route_id,service_id,trip_id\n1001,SVC,T1\n");
        escribir("transfers.txt", "from_stop_id,to_stop_id,transfer_type,min_transfer_time\n");
        escribir("stop_times.txt", "trip_id,arrival_time,departure_time,stop_id,stop_sequence\nT1,08:00:00,08:00:00,A,1\nT1,08:30:00,08:30:00,B,2\n");
        escribir("calendar.txt", "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\nSVC,1,0,0,0,0,0,0,20261026,20261026\n");
        if (eliminarServicio) escribir("calendar_dates.txt", "service_id,date,exception_type\nSVC,20261026,2\n");
    }

    private void escribir(String archivo, String contenido) throws IOException {
        Files.writeString(directorioTemporal.resolve(archivo), contenido);
    }

    private void agregar(String archivo, String contenido) throws IOException {
        Files.writeString(directorioTemporal.resolve(archivo), contenido,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    }
}
