package repositorio;

import modelo.Estacion;
import modelo.Linea;
import modelo.ParadaHorario;
import modelo.Transbordo;
import modelo.Viaje;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RedTransporte {
    private Map<String, Estacion> estacionesById = new HashMap<>();
    private Map<String, Estacion> estacionesByName = new HashMap<>();
    private Map<String, Linea> lineasById = new HashMap<>();
    private Map<String, Viaje> viajesById = new HashMap<>();
    
    private Map<String, List<ParadaHorario>> paradasPorEstacion = new HashMap<>();
    private Map<String, List<ParadaHorario>> paradasPorViaje = new HashMap<>();
    private Map<String, Set<Linea>> lineasPorEstacion = new HashMap<>();
    private Map<String, List<Transbordo>> transbordosPorEstacion = new HashMap<>();

    // --- ESCRITURA ---
    
    public void addEstacion(Estacion estacion) {
        estacionesById.put(estacion.getId(), estacion);
        estacionesByName.put(estacion.getNombre().toLowerCase(), estacion);
        paradasPorEstacion.putIfAbsent(estacion.getId(), new ArrayList<>());
        lineasPorEstacion.putIfAbsent(estacion.getId(), new HashSet<>());
    }

    public void addLinea(Linea linea) { 
        lineasById.put(linea.getId(), linea); 
    }

    public void addViaje(Viaje viaje) {
        viajesById.put(viaje.getId(), viaje);
        paradasPorViaje.putIfAbsent(viaje.getId(), new ArrayList<>());
    }

    public void addParadaHorario(ParadaHorario parada) {
        paradasPorEstacion.get(parada.getStopId()).add(parada);
        paradasPorViaje.get(parada.getTripId()).add(parada);
        
        Viaje viaje = viajesById.get(parada.getTripId());
        if (viaje != null) {
            Linea linea = lineasById.get(viaje.getRouteId());
            if (linea != null) {
                lineasPorEstacion.get(parada.getStopId()).add(linea);
            }
        }
    }

    public void addTransbordo(Transbordo transbordo) {
        transbordosPorEstacion.putIfAbsent(transbordo.getFromStopId(), new ArrayList<>());
        transbordosPorEstacion.get(transbordo.getFromStopId()).add(transbordo);
    }

    public void aplicarAliasComerciales() {
        Linea aliasC8 = new Linea("VIRTUAL_C8", "C8", "Alias Comercial", "868584");
        Linea aliasC2 = new Linea("VIRTUAL_C2", "C2", "Alias Comercial", "00943D");
        Linea aliasC8a = new Linea("VIRTUAL_C8a", "C8a", "Alias Comercial", "868584");
        Linea aliasC3 = new Linea("VIRTUAL_C3", "C3", "Alias Comercial", "952585");
        Linea aliasC1 = new Linea("VIRTUAL_C1", "C1", "Alias Comercial", "75B6E0");
        Linea aliasC10 = new Linea("VIRTUAL_C10", "C10", "Alias Comercial", "BCCF00");
        Linea aliasC4 = new Linea("VIRTUAL_C4", "C4", "Alias Comercial", "2C2A86");

        // Nodos del Pasillo Verde y extensión hacia el Aeropuerto T4
        List<String> tramoC1_C10 = Arrays.asList(
            "10000", "18005", "18004", "18003", "18000", "18001", "18002", 
            "17000", "98003", "98304", "98305"
        );

        for (Map.Entry<String, Set<Linea>> entry : lineasPorEstacion.entrySet()) {
            String estacionId = entry.getKey();
            Set<Linea> lineas = entry.getValue();

            boolean tieneC2 = false, tieneC8_o_b = false, tieneC3 = false, tieneC8a = false;
            boolean tieneC4_ramal = false;

            for (Linea l : lineas) {
                String nombre = l.getShortName().toUpperCase();
                if (nombre.equals("C2")) tieneC2 = true;
                if (nombre.equals("C8") || nombre.equals("C8B")) tieneC8_o_b = true; 
                if (nombre.equals("C3")) tieneC3 = true;
                if (nombre.equals("C8A")) tieneC8a = true;
                if (nombre.startsWith("C4")) tieneC4_ramal = true;
            }

            // 1. Unificar ejes pasantes (C2 <-> C8/C8b y C3 <-> C8a)
            if (tieneC2 && !tieneC8_o_b) lineas.add(aliasC8);
            if (tieneC8_o_b && !tieneC2) lineas.add(aliasC2);
            
            if (tieneC3 && !tieneC8a) lineas.add(aliasC8a);
            if (tieneC8a && !tieneC3) lineas.add(aliasC3);
            
            // 2. Unificar C4a y C4b bajo el sello comercial C4
            if (tieneC4_ramal) {
                lineas.add(aliasC4);
            }
            
            // 3. Forzar coexistencia C1 y C10 en el recorrido compartido completo
            if (tramoC1_C10.contains(estacionId)) {
                lineas.add(aliasC1);
                lineas.add(aliasC10);
            }
        }
    }

    public void limpiarEstacionesFueraDeMadrid() {
        // Eliminar las estaciones que han quedado huérfanas tras el filtro de rutas
        estacionesById.entrySet().removeIf(entry -> getLineasEnEstacion(entry.getKey()).isEmpty());
        estacionesByName.entrySet().removeIf(entry -> getLineasEnEstacion(entry.getValue().getId()).isEmpty());
        
        // Eliminar los transbordos cuyas estaciones origen o destino ya no existan en la red
        transbordosPorEstacion.entrySet().removeIf(entry -> !estacionesById.containsKey(entry.getKey()));
        for (List<Transbordo> lista : transbordosPorEstacion.values()) {
            lista.removeIf(t -> !estacionesById.containsKey(t.getToStopId()));
        }
    }

    public void optimizarIndices() {
        for (List<ParadaHorario> lista : paradasPorEstacion.values()) {
            Collections.sort(lista);
        }
    }

    // --- LECTURA ---

    public Estacion getEstacion(String id) { return estacionesById.get(id); }
    public Estacion getEstacionPorNombre(String nombre) { return estacionesByName.get(nombre.toLowerCase().trim()); }
    public Linea getLinea(String id) { return lineasById.get(id); }
    public Viaje getViaje(String id) { return viajesById.get(id); }
    
    public Set<Linea> getLineasEnEstacion(String stopId) { 
        return lineasPorEstacion.getOrDefault(stopId, new HashSet<>()); 
    }
    
    public List<Estacion> getAllEstaciones() { return new ArrayList<>(estacionesById.values()); }
    public List<Linea> getAllLineas() { return new ArrayList<>(lineasById.values()); }
    public List<Viaje> getAllViajes() { return new ArrayList<>(viajesById.values()); }

    public List<ParadaHorario> getHorariosEstacion(String stopId) {
        return paradasPorEstacion.getOrDefault(stopId, new ArrayList<>());
    }

    public List<ParadaHorario> getRutaDeViaje(String tripId) {
        return paradasPorViaje.getOrDefault(tripId, new ArrayList<>());
    }

    public List<Transbordo> getTransbordosDesde(String stopId) {
        return transbordosPorEstacion.getOrDefault(stopId, new ArrayList<>());
    }

    public List<Estacion> getEstacionesEnRadio(double lat, double lon, double radioKm) {
        List<Estacion> cercanas = new ArrayList<>();
        for (Estacion est : estacionesById.values()) {
            if (est.isActiva() && est.distanciaEnKm(lat, lon) <= radioKm) {
                cercanas.add(est);
            }
        }
        return cercanas;
    }
}