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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * US-004: próximas llegadas a una estación según los horarios planificados (GTFS).
 *
 * En GTFS las horas se cuentan desde el inicio del "día de servicio" y pueden superar
 * las 24:00 (un tren del lunes que llega a las 00:30 del martes figura como 24:30:00 con
 * el calendario del lunes). Por eso, para una consulta en la fecha D se examinan los días
 * de servicio D-1, D y D+1 y cada horario se convierte a fecha y hora reales.
 */
public class ConsultaLlegadas {
    private static final int SEGUNDOS_DIA = 24 * 3600;

    private final RedTransporte red;
    private final Map<String, ParadaHorario> ultimaParadaPorViaje = new HashMap<>();

    public ConsultaLlegadas(RedTransporte red) {
        this.red = red;
    }

    /** Próximas llegadas de todas las líneas. */
    public List<Llegada> proximasLlegadas(Estacion estacion, LocalDateTime desde, int maximo) {
        return proximasLlegadas(estacion, desde, maximo, null);
    }

    /**
     * Devuelve como máximo {@code maximo} llegadas a la estación a partir de {@code desde},
     * ordenadas por hora. Si {@code filtroLinea} no es null, solo se incluyen las de esa línea
     * ("C4" incluye también C4a y C4b, pero "C1" no incluye C10).
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

                LocalDateTime hora = inicioDia.plusSeconds(ph.getArrivalTime());
                if (hora.isBefore(desde)) continue;

                Linea linea = red.getLinea(viaje.getRouteId());
                if (filtroLinea != null && !coincideLinea(linea, filtroLinea)) continue;

                ParadaHorario ultima = ultimaParada(viaje.getId());
                Estacion destino = ultima != null ? red.getEstacion(ultima.getStopId()) : null;
                boolean terminaAqui = ultima != null && ultima.getStopSequence() == ph.getStopSequence();
                resultado.add(new Llegada(hora, linea, destino, terminaAqui, viaje.getId()));
            }
        }

        resultado.sort(Comparator.comparing(Llegada::hora));
        return resultado.size() > maximo ? new ArrayList<>(resultado.subList(0, maximo)) : resultado;
    }

    /**
     * Líneas que paran en la estación en un día de servicio y, para cada una, los destinos
     * (sentidos) de sus trenes. Los trenes que terminan en la estación no aportan sentido;
     * si todos los de una línea terminan allí, la línea aparece con el conjunto vacío.
     * Las líneas se ordenan de forma comercial (C1, C2, ..., C10) y {@code filtroLinea}
     * funciona igual que en {@link #proximasLlegadas}.
     */
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

    /** Orden comercial de líneas: por número y, a igualdad, alfabético (C4 < C4a < C4b < C10). */
    static int compararLineas(String a, String b) {
        String na = a.replaceAll("[^0-9]", "");
        String nb = b.replaceAll("[^0-9]", "");
        int ia = na.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(na);
        int ib = nb.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(nb);
        if (ia != ib) return Integer.compare(ia, ib);
        return a.compareToIgnoreCase(b);
    }

    /** true si la fecha está dentro del periodo cubierto por los horarios cargados. */
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
        // "c4" debe coincidir con "c4a" y "c4b", pero "c1" no con "c10"
        return nombre.startsWith(f) && nombre.length() > f.length()
            && Character.isLetter(nombre.charAt(f.length()));
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
