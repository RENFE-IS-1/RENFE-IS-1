package loader;

import modelo.Estacion;
import modelo.Linea;
import modelo.ParadaHorario;
import modelo.ServicioCalendario;
import modelo.Transbordo;
import modelo.Viaje;
import repositorio.RedTransporte;

import java.io.BufferedReader;
import java.io.File;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GTFSLoader {
    
    public void cargarDesdeDirectorio(String basePath, RedTransporte red) {
        loadStops(ruta(basePath, "stops.txt"), red);
        loadRoutes(ruta(basePath, "routes.txt"), red);
        loadTrips(ruta(basePath, "trips.txt"), red);
        loadTransfers(ruta(basePath, "transfers.txt"), red);
        loadStopTimes(ruta(basePath, "stop_times.txt"), red);
        loadCalendar(ruta(basePath, "calendar.txt"), red);
        loadCalendarDates(ruta(basePath, "calendar_dates.txt"), red);

        File stopTimes = new File(ruta(basePath, "stop_times.txt"));
        if (stopTimes.exists()) red.setFechaDatos(Instant.ofEpochMilli(stopTimes.lastModified()));
        
        red.aplicarAliasComerciales();
        red.limpiarEstacionesFueraDeMadrid(); // Purgado de la red nacional
        red.optimizarIndices();
    }

    private void loadStops(String path, RedTransporte red) {
        try (BufferedReader br = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] p = parseCsv(line);
                if (p.length >= 4) {
                    // Cargamos todas inicialmente; las que sobren se purgarán al final
                    red.addEstacion(new Estacion(p[0].trim(), p[1].trim(), Double.parseDouble(p[2].trim()), Double.parseDouble(p[3].trim())));
                }
            }
        } catch (IOException e) { System.err.println("Error stops.txt: " + e.getMessage()); }
    }
    
    private void loadRoutes(String path, RedTransporte red) {
        try (BufferedReader br = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
            Map<String, Integer> col = indiceColumnas(br.readLine());
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = parseCsv(line);
                try {
                    String id = campo(p, col, "route_id");
                    // FILTRO ESTRUCTURAL: Solo procesar rutas de Cercanías Madrid
                    if (id.startsWith("10")) {
                        String color = campoOpcional(p, col, "route_color", "FFFFFF");
                        red.addLinea(new Linea(id, campo(p, col, "route_short_name"),
                                campo(p, col, "route_long_name"), color));
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.err.println("Línea de routes.txt ignorada: " + line.trim());
                }
            }
        } catch (IOException e) { System.err.println("Error routes.txt: " + e.getMessage()); }
    }
    private void loadTrips(String path, RedTransporte red) {
        try (BufferedReader br = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
            Map<String, Integer> col = indiceColumnas(br.readLine());
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = parseCsv(line);
                try {
                    String routeId = campo(p, col, "route_id");
                    // FILTRO EN CASCADA: Solo guardar el viaje si su ruta es de Madrid
                    if (red.getLinea(routeId) != null) {
                        // block_id es opcional: une viajes que hace el mismo tren (por ejemplo, la
                        // C2 de Guadalajara que en Chamartín sigue como C8a o C8b)
                        red.addViaje(new Viaje(campo(p, col, "trip_id"), routeId, campo(p, col, "service_id"),
                                campoOpcional(p, col, "block_id", null)));
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.err.println("Línea de trips.txt ignorada: " + line.trim());
                }
            }
        } catch (IOException e) { System.err.println("Error trips.txt: " + e.getMessage()); }
    }

    private void loadTransfers(String path, RedTransporte red) {
        try (BufferedReader br = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] p = parseCsv(line);
                if (p.length >= 8) {
                    String fromStop = p[0].trim();
                    String toStop = p[1].trim();
                    int type = p[6].trim().isEmpty() ? 0 : Integer.parseInt(p[6].trim());
                    int minTime = p[7].trim().isEmpty() ? 0 : Integer.parseInt(p[7].trim());
                    red.addTransbordo(new Transbordo(fromStop, toStop, type, minTime));
                }
            }
        } catch (IOException e) { System.err.println("Error transfers.txt: " + e.getMessage()); }
    }

    private void loadStopTimes(String path, RedTransporte red) {
        try (BufferedReader br = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] p = parseCsv(line);
                if (p.length >= 5) {
                    String tripId = p[0].trim();
                    // FILTRO EN CASCADA: Solo procesar el horario si el viaje físico es de Madrid
                    if (red.getViaje(tripId) != null) {
                        red.addParadaHorario(new ParadaHorario(
                            tripId, timeToSeconds(p[1].trim()), timeToSeconds(p[2].trim()), p[3].trim(), Integer.parseInt(p[4].trim())
                        ));
                    }
                }
            }
        } catch (IOException e) { System.err.println("Error stop_times.txt: " + e.getMessage()); }
    }

    private static final DateTimeFormatter FECHA_GTFS = DateTimeFormatter.BASIC_ISO_DATE; // AAAAMMDD

    /** Lee calendar.txt. Las columnas se localizan por la cabecera. */
    private void loadCalendar(String path, RedTransporte red) {
        try (BufferedReader br = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
            Map<String, Integer> col = indiceColumnas(br.readLine());
            String[] diasCol = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = parseCsv(line);
                try {
                    boolean[] dias = new boolean[7];
                    for (int i = 0; i < 7; i++) dias[i] = campo(p, col, diasCol[i]).equals("1");
                    red.addServicioCalendario(new ServicioCalendario(
                        campo(p, col, "service_id"), dias,
                        LocalDate.parse(campo(p, col, "start_date"), FECHA_GTFS),
                        LocalDate.parse(campo(p, col, "end_date"), FECHA_GTFS)));
                } catch (DateTimeParseException | ArrayIndexOutOfBoundsException e) {
                    System.err.println("Línea de calendar.txt ignorada: " + line.trim());
                }
            }
        } catch (IOException e) { System.err.println("Error calendar.txt: " + e.getMessage()); }
    }

    /** Lee calendar_dates.txt (opcional en GTFS: si no existe, no se considera un error). */
    private void loadCalendarDates(String path, RedTransporte red) {
        if (!new File(path).exists()) return;
        try (BufferedReader br = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
            Map<String, Integer> col = indiceColumnas(br.readLine());
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = parseCsv(line);
                try {
                    red.addExcepcionCalendario(
                        campo(p, col, "service_id"),
                        LocalDate.parse(campo(p, col, "date"), FECHA_GTFS),
                        Integer.parseInt(campo(p, col, "exception_type")));
                } catch (DateTimeParseException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    System.err.println("Línea de calendar_dates.txt ignorada: " + line.trim());
                }
            }
        } catch (IOException e) { System.err.println("Error calendar_dates.txt: " + e.getMessage()); }
    }

    private Map<String, Integer> indiceColumnas(String cabecera) {
        Map<String, Integer> col = new HashMap<>();
        if (cabecera == null) return col;
        String[] nombres = parseCsv(cabecera.replace("\uFEFF", ""));
        for (int i = 0; i < nombres.length; i++) col.put(nombres[i].trim(), i);
        return col;
    }

    private String campo(String[] p, Map<String, Integer> col, String nombre) {
        Integer i = col.get(nombre);
        if (i == null) throw new ArrayIndexOutOfBoundsException("Falta la columna " + nombre);
        return p[i].trim();
    }

    /** Une directorio y nombre de archivo sin exigir que el directorio termine en separador. */
    private String campoOpcional(String[] p, Map<String, Integer> col, String nombre, String valorPorDefecto) {
        Integer i = col.get(nombre);
        return i == null || i >= p.length || p[i].isBlank() ? valorPorDefecto : p[i].trim();
    }
    private static String ruta(String directorio, String archivo) {
        return new File(directorio, archivo).getPath();
    }

    /**
     * Divide una fila CSV respetando comas entrecomilladas y comillas dobles escapadas, como
     * permite GTFS. Se procesa una línea cada vez porque los archivos GTFS usados no incluyen
     * saltos de línea dentro de un campo.
     */
    private static String[] parseCsv(String line) {
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreComillas = false;
        for (int i = 0; i < line.length(); i++) {
            char actual = line.charAt(i);
            if (actual == '"') {
                if (entreComillas && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++;
                } else {
                    entreComillas = !entreComillas;
                }
            } else if (actual == ',' && !entreComillas) {
                campos.add(campo.toString());
                campo.setLength(0);
            } else {
                campo.append(actual);
            }
        }
        campos.add(campo.toString());
        return campos.toArray(new String[0]);
    }
    private int timeToSeconds(String time) {
        if (time == null || time.isEmpty()) return 0;
        String[] p = time.split(":");
        return Integer.parseInt(p[0]) * 3600 + Integer.parseInt(p[1]) * 60 + Integer.parseInt(p[2]);
    }
}