package repositorio;

import modelo.Estacion;
import modelo.Linea;
import modelo.ParadaHorario;
import modelo.ServicioCalendario;
import modelo.Transbordo;
import modelo.Viaje;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class RedTransporte {
    private Map<String, Estacion> estacionesById = new HashMap<>();
    private Map<String, Estacion> estacionesByName = new HashMap<>();
    private Map<String, Linea> lineasById = new HashMap<>();
    private Map<String, Viaje> viajesById = new HashMap<>();
    
    private Map<String, List<ParadaHorario>> paradasPorEstacion = new HashMap<>();
    private Map<String, List<ParadaHorario>> paradasPorViaje = new HashMap<>();
    private Map<String, Set<Linea>> lineasPorEstacion = new HashMap<>();
    private Map<String, List<Transbordo>> transbordosPorEstacion = new HashMap<>();

    private Map<String, ServicioCalendario> calendarioPorServicio = new HashMap<>();
    private LocalDate inicioValidez;
    private LocalDate finValidez;
    private Instant fechaDatos;

    // --- TIEMPO REAL ---
    private volatile boolean posicionesTiempoRealActivas = false;
    private volatile boolean horariosTiempoRealActivos = false;
    private volatile boolean incidenciasTiempoRealActivas = false;
    
    private final Map<String, Integer> retrasosPorViajeSegundos = new ConcurrentHashMap<>();
    private final List<String> incidencias = new CopyOnWriteArrayList<>();

    public boolean isPosicionesTiempoRealActivas() { return posicionesTiempoRealActivas; }
    public void setPosicionesTiempoRealActivas(boolean activas) { this.posicionesTiempoRealActivas = activas; }

    public boolean isHorariosTiempoRealActivos() { return horariosTiempoRealActivos; }
    public void setHorariosTiempoRealActivos(boolean activos) { this.horariosTiempoRealActivos = activos; }

    public boolean isIncidenciasTiempoRealActivas() { return incidenciasTiempoRealActivas; }
    public void setIncidenciasTiempoRealActivas(boolean activas) { this.incidenciasTiempoRealActivas = activas; }

    public void registrarRetrasoViaje(String tripId, int retrasoSegundos) {
        if (tripId != null) {
            retrasosPorViajeSegundos.put(tripId, retrasoSegundos);
        }
    }

    public int getRetrasoViajeSegundos(String tripId) {
        return retrasosPorViajeSegundos.getOrDefault(tripId, 0);
    }

    public void addIncidencia(String incidencia) {
        if (incidencia != null && !incidencia.isBlank()) {
            this.incidencias.add(incidencia);
        }
    }

    public List<String> getIncidencias() {
        return new ArrayList<>(this.incidencias);
    }

    public void limpiarIncidencias() {
        this.incidencias.clear();
    }

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

    public void addServicioCalendario(ServicioCalendario servicio) {
        calendarioPorServicio.put(servicio.getServiceId(), servicio);
        ampliarValidez(servicio.getInicio());
        ampliarValidez(servicio.getFin());
    }

    public void addExcepcionCalendario(String serviceId, LocalDate fecha, int exceptionType) {
        calendarioPorServicio
            .computeIfAbsent(serviceId, ServicioCalendario::soloExcepciones)
            .addExcepcion(fecha, exceptionType);
        if (exceptionType == 1) ampliarValidez(fecha);
    }

    private void ampliarValidez(LocalDate fecha) {
        if (fecha == null) return;
        if (inicioValidez == null || fecha.isBefore(inicioValidez)) inicioValidez = fecha;
        if (finValidez == null || fecha.isAfter(finValidez)) finValidez = fecha;
    }

    public void setFechaDatos(Instant fechaDatos) { this.fechaDatos = fechaDatos; }

    public void aplicarAliasComerciales() {
        Linea aliasC8 = new Linea("VIRTUAL_C8", "C8", "Alias Comercial", "868584");
        Linea aliasC2 = new Linea("VIRTUAL_C2", "C2", "Alias Comercial", "00943D");
        Linea aliasC1 = new Linea("VIRTUAL_C1", "C1", "Alias Comercial", "75B6E0");
        Linea aliasC10 = new Linea("VIRTUAL_C10", "C10", "Alias Comercial", "BCCF00");
        Linea aliasC4 = new Linea("VIRTUAL_C4", "C4", "Alias Comercial", "2C2A86");

        List<String> tramoC1_C10 = Arrays.asList(
            "10000", "18005", "18004", "18003", "18000", "18001", "18002", 
            "17000", "98003", "98304", "98305"
        );

        for (Map.Entry<String, Set<Linea>> entry : lineasPorEstacion.entrySet()) {
            String estacionId = entry.getKey();
            Set<Linea> lineas = entry.getValue();

            boolean tieneC2 = false, tieneC8_o_b = false;
            boolean tieneC4_ramal = false;

            for (Linea l : lineas) {
                String nombre = l.getShortName().toUpperCase();
                if (nombre.equals("C2")) tieneC2 = true;
                if (nombre.equals("C8") || nombre.equals("C8B")) tieneC8_o_b = true; 
                if (nombre.startsWith("C4")) tieneC4_ramal = true;
            }

            if (tieneC2 && !tieneC8_o_b) lineas.add(aliasC8);
            if (tieneC8_o_b && !tieneC2) lineas.add(aliasC2);
            
            if (tieneC4_ramal) {
                lineas.add(aliasC4);
            }
            
            if (tramoC1_C10.contains(estacionId)) {
                lineas.add(aliasC1);
                lineas.add(aliasC10);
            }
        }
    }

    public void limpiarEstacionesFueraDeMadrid() {
        estacionesById.entrySet().removeIf(entry -> getLineasEnEstacion(entry.getKey()).isEmpty());
        estacionesByName.entrySet().removeIf(entry -> getLineasEnEstacion(entry.getValue().getId()).isEmpty());
        
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

    public List<Estacion> getEstacionesDeLinea(String shortName) {
        if (shortName == null || shortName.isBlank()) return new ArrayList<>();

        Set<String> routeIds = new HashSet<>();
        for (Linea linea : lineasById.values()) {
            if (shortName.equalsIgnoreCase(linea.getShortName())) {
                routeIds.add(linea.getId());
            }
        }

        List<ParadaHorario> mejorRecorrido = new ArrayList<>();
        for (Viaje viaje : viajesById.values()) {
            if (!routeIds.contains(viaje.getRouteId())) continue;

            List<ParadaHorario> recorrido = new ArrayList<>(getRutaDeViaje(viaje.getId()));
            recorrido.sort((a, b) -> Integer.compare(a.getStopSequence(), b.getStopSequence()));
            if (recorrido.size() > mejorRecorrido.size()) mejorRecorrido = recorrido;
        }

        Map<String, Estacion> estaciones = new LinkedHashMap<>();
        for (ParadaHorario parada : mejorRecorrido) {
            Estacion estacion = estacionesById.get(parada.getStopId());
            if (estacion != null) estaciones.putIfAbsent(estacion.getId(), estacion);
        }
        return new ArrayList<>(estaciones.values());
    }

    private final Map<String, List<Estacion>> recorridoCompletoCache = new ConcurrentHashMap<>();

    public List<String> getRamales(String linea) {
        if (linea == null || linea.isBlank()) return new ArrayList<>();
        Set<String> ramales = new TreeSet<>();
        boolean existe = false;
        for (Linea l : lineasById.values()) {
            String nombre = l.getShortName();
            if (nombre.equalsIgnoreCase(linea)) existe = true;
            if (nombre.length() == linea.length() + 1 && nombre.regionMatches(true, 0, linea, 0, linea.length())
                    && Character.isLowerCase(nombre.charAt(linea.length()))) ramales.add(nombre);
        }
        ramales.removeIf(r -> getEstacionesDeLinea(r).isEmpty()); 
        if (ramales.size() >= 2) return new ArrayList<>(ramales);
        return existe ? new ArrayList<>(List.of(linea.toUpperCase())) : new ArrayList<>();
    }

    public List<Estacion> getRecorridoCompleto(String shortName) {
        if (shortName == null || shortName.isBlank()) return new ArrayList<>();
        return new ArrayList<>(recorridoCompletoCache.computeIfAbsent(shortName, this::calcularRecorridoCompleto));
    }

    private List<Estacion> calcularRecorridoCompleto(String shortName) {
        Map<String, List<String>> propias = new LinkedHashMap<>();
        Map<String, Integer> vecesPropia = new HashMap<>();
        Map<String, Integer> enlazadas = new HashMap<>();
        Map<String, List<String>> secuenciaEnlazada = new HashMap<>();
        Map<String, List<Viaje>> porBloque = new HashMap<>();
        for (Viaje v : viajesById.values()) {
            if (v.getBlockId() != null) porBloque.computeIfAbsent(v.getBlockId(), k -> new ArrayList<>()).add(v);
        }
        int viajesLinea = 0;
        for (Viaje v : viajesById.values()) {
            Linea l = lineasById.get(v.getRouteId());
            if (l == null || !l.getShortName().equals(shortName)) continue;
            viajesLinea++;
            List<String> seq = secuencia(v.getId());
            if (seq.size() >= 2) {
                propias.putIfAbsent(String.join(",", seq), seq);
                vecesPropia.merge(String.join(",", seq), 1, Integer::sum);
            }
            if (v.getBlockId() == null) continue;
            List<Viaje> bloque = new ArrayList<>(porBloque.get(v.getBlockId()));
            bloque.sort((a, b) -> Integer.compare(primeraSalida(a.getId()), primeraSalida(b.getId())));
            int i = bloque.indexOf(v);
            for (int j : new int[]{i - 1, i + 1}) {
                if (j < 0 || j >= bloque.size()) continue;
                Linea otra = lineasById.get(bloque.get(j).getRouteId());
                if (otra == null || otra.getShortName().equals(shortName)) continue;
                List<String> s2 = secuencia(bloque.get(j).getId());
                if (s2.size() < 2) continue;
                String clave = String.join(",", s2);
                enlazadas.merge(clave, 1, Integer::sum);
                secuenciaEnlazada.put(clave, s2);
            }
        }
        if (propias.isEmpty()) return new ArrayList<>();
        int masLargo = 0;
        for (List<String> seq : propias.values()) masLargo = Math.max(masLargo, seq.size());
        String base = null;
        for (Map.Entry<String, List<String>> e : propias.entrySet()) {
            if (e.getValue().size() * 2 < masLargo) continue;
            if (base == null || vecesPropia.get(e.getKey()) > vecesPropia.get(base)
                    || (vecesPropia.get(e.getKey()).equals(vecesPropia.get(base)) && e.getValue().size() > propias.get(base).size())) {
                base = e.getKey();
            }
        }
        List<String> camino = new ArrayList<>(propias.get(base));
        List<List<String>> candidatas = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : propias.entrySet()) {
            if (vecesPropia.get(e.getKey()) >= 0.05 * viajesLinea) candidatas.add(e.getValue());
        }
        for (Map.Entry<String, Integer> e : enlazadas.entrySet()) {
            if (e.getValue() >= 0.2 * viajesLinea) candidatas.add(secuenciaEnlazada.get(e.getKey()));
        }
        boolean cambiado = true;
        while (cambiado) {
            cambiado = false;
            for (List<String> c : candidatas) {
                for (List<String> s : List.of(c, invertida(c))) {
                    String ultima = camino.get(camino.size() - 1), primera = camino.get(0);
                    int k = s.indexOf(ultima);
                    if (k >= 0 && k < s.size() - 1 && s.indexOf(primera) < 0 && !contieneAlguna(camino, s.subList(k + 1, s.size()))) {
                        camino.addAll(s.subList(k + 1, s.size())); cambiado = true;
                    }
                    k = s.indexOf(primera);
                    if (k > 0 && s.indexOf(ultima) < 0 && !contieneAlguna(camino, s.subList(0, k))) {
                        camino.addAll(0, s.subList(0, k)); cambiado = true;
                    }
                }
            }
        }
        List<Estacion> resultado = new ArrayList<>();
        for (String id : camino) {
            Estacion e = estacionesById.get(id);
            if (e != null) resultado.add(e);
        }
        return resultado;
    }

    private List<String> secuencia(String tripId) {
        List<ParadaHorario> paradas = new ArrayList<>(getRutaDeViaje(tripId));
        paradas.sort((a, b) -> Integer.compare(a.getStopSequence(), b.getStopSequence()));
        List<String> ids = new ArrayList<>();
        for (ParadaHorario ph : paradas) if (ids.isEmpty() || !ids.get(ids.size() - 1).equals(ph.getStopId())) ids.add(ph.getStopId());
        return ids;
    }

    private int primeraSalida(String tripId) {
        int m = Integer.MAX_VALUE;
        for (ParadaHorario ph : getRutaDeViaje(tripId)) m = Math.min(m, ph.getDepartureTime());
        return m;
    }

    private static List<String> invertida(List<String> s) {
        List<String> r = new ArrayList<>(s); Collections.reverse(r); return r;
    }

    private static boolean contieneAlguna(List<String> camino, List<String> nuevas) {
        for (String n : nuevas) if (camino.contains(n)) return true;
        return false;
    }

    public boolean isServicioActivo(String serviceId, LocalDate fecha) {
        ServicioCalendario servicio = calendarioPorServicio.get(serviceId);
        return servicio != null && servicio.activoEn(fecha);
    }

    public boolean hayCalendario() { return !calendarioPorServicio.isEmpty(); }
    public LocalDate getInicioValidez() { return inicioValidez; }
    public LocalDate getFinValidez() { return finValidez; }
    public Instant getFechaDatos() { return fechaDatos; }

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