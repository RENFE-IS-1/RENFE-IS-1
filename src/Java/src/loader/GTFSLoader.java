package loader;

import modelo.Estacion;
import modelo.Linea;
import modelo.ParadaHorario;
import modelo.Transbordo;
import modelo.Viaje;
import repositorio.RedTransporte;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class GTFSLoader {
    
    public void cargarDesdeDirectorio(String basePath, RedTransporte red) {
        loadStops(basePath + "stops.txt", red);
        loadRoutes(basePath + "routes.txt", red);
        loadTrips(basePath + "trips.txt", red);
        loadTransfers(basePath + "transfers.txt", red);
        loadStopTimes(basePath + "stop_times.txt", red);
        
        red.aplicarAliasComerciales();
        red.limpiarEstacionesFueraDeMadrid(); // Purgado de la red nacional
        red.optimizarIndices();
    }

    private void loadStops(String path, RedTransporte red) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length >= 4) {
                    // Cargamos todas inicialmente; las que sobren se purgarán al final
                    red.addEstacion(new Estacion(p[0].trim(), p[1].trim(), Double.parseDouble(p[2].trim()), Double.parseDouble(p[3].trim())));
                }
            }
        } catch (IOException e) { System.err.println("Error stops.txt: " + e.getMessage()); }
    }

    private void loadRoutes(String path, RedTransporte red) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length >= 6) {
                    String id = p[0].trim();
                    // FILTRO ESTRUCTURAL: Solo procesar rutas de Cercanías Madrid
                    if (id.startsWith("10")) { 
                        String color = p.length > 4 ? p[4].trim() : "FFFFFF";
                        red.addLinea(new Linea(id, p[1].trim(), p[2].trim(), color));
                    }
                }
            }
        } catch (IOException e) { System.err.println("Error routes.txt: " + e.getMessage()); }
    }

    private void loadTrips(String path, RedTransporte red) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length >= 3) {
                    String routeId = p[0].trim();
                    // FILTRO EN CASCADA: Solo guardar el viaje si su ruta es de Madrid
                    if (red.getLinea(routeId) != null) {
                        red.addViaje(new Viaje(p[2].trim(), routeId, p[1].trim()));
                    }
                }
            }
        } catch (IOException e) { System.err.println("Error trips.txt: " + e.getMessage()); }
    }

    private void loadTransfers(String path, RedTransporte red) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", -1);
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
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            br.readLine();
            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", -1);
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

    private int timeToSeconds(String time) {
        if (time == null || time.isEmpty()) return 0;
        String[] p = time.split(":");
        return Integer.parseInt(p[0]) * 3600 + Integer.parseInt(p[1]) * 60 + Integer.parseInt(p[2]);
    }
}