package repositorio;

import modelo.Estacion;
import modelo.Linea;
import modelo.ParadaHorario;
import modelo.RetrasoParada;
import modelo.ServicioCalendario;
import modelo.Transbordo;
import modelo.Viaje;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class RedTransporte {
    private Map<String, Estacion> estacionesById = new HashMap<>();
    private Map<String, Estacion> estacionesByName = new HashMap<>();
    private Map<String, Linea> lineasById = new HashMap<>();
    private Map<String, Viaje> viajesById = new HashMap<>();
    
    private Map<String, List<ParadaHorario>> paradasPorEstacion = new HashMap<>();
    private Map<String, List<ParadaHorario>> paradasPorViaje = new HashMap<>();
    private Map<String, Set<Linea>> lineasPorEstacion = new HashMap<>();
    private Map<String, List<Transbordo>> transbordosPorEstacion = new HashMap<>();

    // Calendario de servicios (calendar.txt / calendar_dates.txt)
    private Map<String, ServicioCalendario> calendarioPorServicio = new HashMap<>();
    private LocalDate inicioValidez;
    private LocalDate finValidez;
    private Instant fechaDatos; // fecha de los archivos de datos cargados

    // --- TIEMPO REAL (GTFS-RT de Renfe) ---
    // Lo rellena ClienteApiRenfe y ActualizadorTiempoReal lo vuelve a pedir cada minuto. Cada
    // actualización sustituye los datos de golpe, así quien los lee nunca ve una lista a medias.
    private volatile boolean posicionesTiempoRealActivas = false;
    private volatile boolean horariosTiempoRealActivos = false;
    private volatile boolean incidenciasTiempoRealActivas = false;

    private volatile Map<String, List<RetrasoParada>> retrasosPorViaje = Map.of();
    private volatile List<String> incidencias = List.of();

    public boolean isPosicionesTiempoRealActivas() { return posicionesTiempoRealActivas; }
    public void setPosicionesTiempoRealActivas(boolean activas) { this.posicionesTiempoRealActivas = activas; }

    public boolean isHorariosTiempoRealActivos() { return horariosTiempoRealActivos; }
    public void setHorariosTiempoRealActivos(boolean activos) { this.horariosTiempoRealActivos = activos; }

    public boolean isIncidenciasTiempoRealActivas() { return incidenciasTiempoRealActivas; }
    public void setIncidenciasTiempoRealActivas(boolean activas) { this.incidenciasTiempoRealActivas = activas; }

    /** Sustituye todos los retrasos (por trip_id, uno por parada) por los de la última descarga. */
    public void reemplazarRetrasos(Map<String, List<RetrasoParada>> retrasos) {
        Map<String, List<RetrasoParada>> copia = new HashMap<>();
        retrasos.forEach((viaje, lista) -> copia.put(viaje, List.copyOf(lista)));
        this.retrasosPorViaje = Map.copyOf(copia);
    }

    /**
     * Retraso en segundos de un viaje en una parada, según la última descarga. Como dice GTFS-RT,
     * si la parada no trae su propio dato vale el de la parada anterior más cercana que sí lo traiga
     * (el retraso se arrastra). Devuelve 0 si no hay ninguno antes: se usa el horario programado.
     */
    /**
     * Retraso en segundos de un viaje en una parada, según la última descarga. Como dice GTFS-RT,
     * si la parada no trae su propio dato vale el de la parada anterior más cercana que sí lo traiga
     * (el retraso se arrastra). Devuelve 0 si no hay ninguno antes: se usa el horario programado.
     */
    public int getRetrasoEnParada(String tripId, String stopId) {
        if (tripId == null || stopId == null || retrasosPorViaje.isEmpty()) return 0;

        // 1. Búsqueda exacta
        List<RetrasoParada> datos = retrasosPorViaje.get(tripId);

        // 2. Si no coincide exacto, busca si el tripId de la consulta está contenido en la clave de Renfe (o viceversa)
        if (datos == null) {
            for (Map.Entry<String, List<RetrasoParada>> entry : retrasosPorViaje.entrySet()) {
                String claveRenfe = entry.getKey();
                if (claveRenfe.endsWith(tripId) || claveRenfe.contains(tripId) || tripId.contains(claveRenfe)) {
                    datos = entry.getValue();
                    break;
                }
            }
        }

        if (datos == null || datos.isEmpty()) return 0;

        List<ParadaHorario> paradas = new ArrayList<>(getRutaDeViaje(tripId));
        paradas.sort(Comparator.comparingInt(ParadaHorario::getStopSequence));
        int objetivo = -1;
        for (int i = 0; i < paradas.size(); i++) {
            if (stopId.equals(paradas.get(i).getStopId())) { objetivo = i; break; }
        }
        if (objetivo < 0) return 0;

        RetrasoParada elegido = null;
        int posElegido = -1;
        for (RetrasoParada r : datos) {
            int pos = posicionEnViaje(r, paradas);
            if (pos >= 0 && pos <= objetivo && pos >= posElegido) { elegido = r; posElegido = pos; }
        }
        return (elegido == null || elegido.retrasoSegundos() == null) ? 0 : elegido.retrasoSegundos();
    }
    
    /**
     * Devuelve el retraso en minutos de un viaje en una parada (convirtiendo los segundos de GTFS-RT).
     */
    public int getRetrasoMinutos(String tripId, String stopId) {
        int retrasoSegundos = getRetrasoEnParada(tripId, stopId);
        return Math.max(0, retrasoSegundos / 60);
    }
    /** Retraso de un viaje en su primera parada con dato (0 si no hay). Para una parada concreta, getRetrasoEnParada. */
    public int getRetrasoViajeSegundos(String tripId) {
        List<RetrasoParada> datos = tripId == null ? null : retrasosPorViaje.get(tripId);
        if (datos == null) return 0;
        for (RetrasoParada r : datos) if (r.retrasoSegundos() != null) return r.retrasoSegundos();
        return 0;
    }

    /** Posición en el viaje de la parada a la que se refiere un retraso (por stop_sequence o, si no, por stop_id). */
    private static int posicionEnViaje(RetrasoParada r, List<ParadaHorario> paradas) {
        if (r.esDeTodoElViaje()) return 0;
        for (int i = 0; i < paradas.size(); i++) {
            ParadaHorario p = paradas.get(i);
            if (r.stopSequence() != null ? p.getStopSequence() == r.stopSequence() : p.getStopId().equals(r.stopId())) return i;
        }
        return -1;
    }

    /** Sustituye todas las incidencias por las de la última descarga. */
    public void reemplazarIncidencias(List<String> nuevas) {
        this.incidencias = List.copyOf(nuevas);
    }

    /** Incidencias de la última descarga (lista de solo lectura). */
    public List<String> getIncidencias() {
        return incidencias;
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

        // Nodos del Pasillo Verde y extensión hacia el Aeropuerto T4
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

            // 1. Eje pasante C2 <-> C8: los trenes de la C2 siguen en Chamartín como C8a/C8b
            //    (mismo block_id en el GTFS). La C3 ya no enlaza con la C8a desde 2024: quien va
            //    de la C3 a El Escorial cambia de tren en Chamartín.
            if (tieneC2 && !tieneC8_o_b) lineas.add(aliasC8);
            if (tieneC8_o_b && !tieneC2) lineas.add(aliasC2);
            
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

    /**
     * Devuelve las estaciones de un recorrido representativo de una línea.
     * Si una línea tiene varios recorridos GTFS, se elige el viaje con más
     * paradas para incluir el recorrido completo disponible en la red.
     */
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

    /** Recorridos completos ya calculados, por nombre de línea (se piden desde la web). */
    private final Map<String, List<Estacion>> recorridoCompletoCache = new ConcurrentHashMap<>();

    /**
     * Ramales con los que se muestra una línea en la web. Si hay al menos dos líneas con trenes que
     * se llaman igual más una letra (C4a y C4b para "C4", C8a y C8b para "C8"), son sus ramales; si
     * no, la línea es su único ramal.
     */
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
        ramales.removeIf(r -> getEstacionesDeLinea(r).isEmpty()); // solo las que tienen trenes
        if (ramales.size() >= 2) return new ArrayList<>(ramales);
        return existe ? new ArrayList<>(List.of(linea.toUpperCase())) : new ArrayList<>();
    }

    /**
     * Recorrido completo de una línea, en orden. Se parte del recorrido más frecuente entre los
     * largos (al menos la mitad de paradas que el más largo), para no tomar como base un servicio
     * ocasional. Se alarga por sus extremos con otros recorridos de la misma línea que hagan al menos
     * el 5 % de sus viajes (por ejemplo, la C8a de El Escorial a Santa María de la Alameda) y con los
     * tramos que hace el mismo tren antes o después con otra línea, si eso pasa en al menos el 20 %
     * de sus viajes (la C2 de Guadalajara que sigue como C8a/C8b, que Renfe anuncia como C8).
     */
    public List<Estacion> getRecorridoCompleto(String shortName) {
        if (shortName == null || shortName.isBlank()) return new ArrayList<>();
        return new ArrayList<>(recorridoCompletoCache.computeIfAbsent(shortName, this::calcularRecorridoCompleto));
    }

    private List<Estacion> calcularRecorridoCompleto(String shortName) {
        // Secuencias de paradas de la línea y de los trenes que siguen o vienen de otra línea
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
        // Se alarga el camino por los dos extremos mientras alguna secuencia lo continúe
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

    /** Indica si los viajes con este service_id circulan en la fecha dada. */
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
