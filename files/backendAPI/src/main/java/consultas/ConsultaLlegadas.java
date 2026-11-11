package consultas;

import modelo.Estacion;
import modelo.Linea;
import modelo.Llegada;
import modelo.ParadaHorario;
import modelo.Viaje;
import repositorio.RedTransporte;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * US-004: próximas llegadas a una estación según los horarios planificados (GTFS) y tiempo real.
 */
public class ConsultaLlegadas {
    private static final int SEGUNDOS_DIA = 24 * 3600;

    private final RedTransporte red;
    private final Map<String, ParadaHorario> ultimaParadaPorViaje = new ConcurrentHashMap<>();

    public ConsultaLlegadas(RedTransporte red) {
        this.red = red;
    }

    /** Próximas llegadas de todas las líneas. */
    public List<Llegada> proximasLlegadas(Estacion estacion, LocalDateTime desde, int maximo) {
        return proximasLlegadas(estacion, desde, maximo, null);
    }

    /**
     * Devuelve como máximo {@code maximo} llegadas a la estación a partir de {@code desde},
     * ordenadas por hora. Si {@code filtroLinea} no es null, solo se incluyen las de esa línea.
     */
    public List<Llegada> proximasLlegadas(Estacion estacion, LocalDateTime desde, int maximo, String filtroLinea) {
        List<Llegada> resultado = new ArrayList<>();
        if (estacion == null || maximo <= 0) return resultado;

        LocalDate fechaConsulta = desde.toLocalDate();
        for (int desfase = -1; desfase <= 1; desfase++) {
            LocalDate diaServicio = fechaConsulta.plusDays(desfase);
            LocalDateTime inicioDia = diaServicio.atStartOfDay();

            for (ParadaHorario ph : red.getHorariosEstacion(estacion.getId())) {
                Viaje viaje = red.getViaje(ph.getTripId());
                if (viaje == null || !red.isServicioActivo(viaje.getServiceId(), diaServicio)) continue;

                LocalDateTime horaTeorica = inicioDia.plusSeconds(ph.getArrivalTime());
                
                // 1. Obtener retraso en minutos y tiempo real desde RedTransporte
                int retrasoMinutos = red.getRetrasoMinutos(viaje.getId(), ph.getStopId());
                LocalDateTime horaEstimada = horaTeorica.plusMinutes(retrasoMinutos);

                // 2. Comprobar que la hora estimada no haya pasado respecto a la consulta
                if (horaEstimada.isBefore(desde)) continue;

                Linea linea = red.getLinea(viaje.getRouteId());
                if (filtroLinea != null && !coincideLinea(linea, filtroLinea)) continue;

                ParadaHorario ultima = ultimaParada(viaje.getId());
                Estacion destino = ultima != null ? red.getEstacion(ultima.getStopId()) : null;
                boolean terminaAqui = ultima != null && ultima.getStopSequence() == ph.getStopSequence();

                // 3. Determinar si el viaje viene reportado por el tiempo real de Renfe
                boolean esRT = red.isHorariosTiempoRealActivos();

                // 4. Crear Llegada pasando la información completa de tiempo real
                resultado.add(new Llegada(
                    horaEstimada,
                    linea,
                    destino,
                    terminaAqui,
                    viaje.getId(),
                    retrasoMinutos,
                    esRT,
                    esRT ? LocalDateTime.now() : null
                ));
            }
        }

        resultado.sort(Comparator.comparing(Llegada::hora));
        return resultado.size() > maximo ? new ArrayList<>(resultado.subList(0, maximo)) : resultado;
    }

    public Map<String, Set<String>> lineasYSentidos(Estacion estacion, LocalDate fecha, String filtroLinea) {
        Map<String, Set<String>> resultado = new TreeMap<>(ConsultaLlegadas::compararLineas);
        if (estacion == null) return resultado;

        for (ParadaHorario ph : red.getHorariosEstacion(estacion.getId())) {
            Viaje viaje = red.getViaje(ph.getTripId());
            if (viaje == null || !red.isServicioActivo(viaje.getServiceId(), fecha)) continue;
            Linea linea = red.getLinea(viaje.getRouteId());
            if (linea == null || (filtroLinea != null && !coincideLinea(linea, filtroLinea))) continue;

            Set<String> destinos = resultado.computeIfAbsent(linea.getShortName(), k -> new TreeSet<>());
            ParadaHorario ultima = ultimaParada(viaje.getId());
            boolean terminaAqui = ultima != null && ultima.getStopSequence() == ph.getStopSequence();
            Estacion destino = ultima != null ? red.getEstacion(ultima.getStopId()) : null;
            if (!terminaAqui && destino != null) destinos.add(destino.getNombre());
        }
        return resultado;
    }

    static int compararLineas(String a, String b) {
        String na = a.replaceAll("[^0-9]", "");
        String nb = b.replaceAll("[^0-9]", "");
        int ia = na.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(na);
        int ib = nb.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(nb);
        if (ia != ib) return Integer.compare(ia, ib);
        return a.compareToIgnoreCase(b);
    }

    public boolean fechaCubierta(LocalDate fecha) {
        LocalDate inicio = red.getInicioValidez();
        LocalDate fin = red.getFinValidez();
        return inicio != null && fin != null && !fecha.isBefore(inicio) && !fecha.isAfter(fin);
    }

    private ParadaHorario ultimaParada(String tripId) {
        return ultimaParadaPorViaje.computeIfAbsent(tripId, id -> {
            ParadaHorario ultima = null;
            for (ParadaHorario ph : red.getRutaDeViaje(id)) {
                if (ultima == null || ph.getStopSequence() > ultima.getStopSequence()) ultima = ph;
            }
            return ultima;
        });
    }

    static boolean coincideLinea(Linea linea, String filtro) {
        if (linea == null) return false;
        String nombre = normalizar(linea.getShortName());
        String f = normalizar(filtro);
        if (nombre.equals(f)) return true;
        return nombre.startsWith(f) && nombre.length() > f.length()
            && Character.isLetter(nombre.charAt(f.length()));
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}