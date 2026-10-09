package servicio;

import modelo.Estacion;
import modelo.Linea;
import modelo.ParadaHorario;
import modelo.Ruta;
import modelo.Tramo;
import modelo.Transbordo;
import modelo.Viaje;
import repositorio.RedTransporte;

import java.time.LocalTime;
import java.util.*;

/**
 * Búsqueda de rutas entre dos estaciones sobre los recorridos reales de los trenes.
 *
 * <p><b>Modelo.</b> Los viajes del GTFS con la misma línea y la misma secuencia de estaciones
 * forman un <i>recorrido</i>. Con un tren se va de A a B si algún recorrido pasa por A y después
 * por B. La duración de cada tramo sale de los horarios de un viaje real de ese recorrido. Los
 * transbordos a pie de transfers.txt son tramos "A pie" con su tiempo oficial.
 *
 * <p><b>Algoritmo.</b>
 * <ol>
 *   <li>Se calcula, por rondas hacia atrás desde el destino, cuántos tramos (trenes o
 *       transbordos a pie) hacen falta como mínimo desde cada estación ({@code trenesHasta}).
 *       Para el origen da el mínimo de tramos de cualquier ruta, como el BFS anterior.</li>
 *   <li>Se enumeran las rutas que usan ese mínimo o uno más, con una búsqueda en profundidad que
 *       descarta los caminos que ya no pueden llegar dentro de ese límite (poda con
 *       {@code trenesHasta}) y los que llegan a una estación con los mismos tramos y la misma
 *       última línea que otro camino igual de rápido o más.</li>
 *   <li>Las rutas se agrupan por secuencia de líneas (por ejemplo "C3 → C7") y de cada grupo se
 *       queda la más rápida. Se ofrecen todas las que usan el mínimo de transbordos y, con un
 *       transbordo más, solo las que ahorran al menos {@code AHORRO_MINIMO} minutos.</li>
 * </ol>
 *
 * <p>Las horas son orientativas: parten de la hora actual y no esperan al próximo tren real
 * (eso es US-010).
 */
public class ServicioBuscadorRutas {
    /** Tiempo de espera que se suma en cada transbordo entre trenes. */
    private static final int MINUTOS_TRANSBORDO = 2;
    /** Una ruta con un transbordo más solo se ofrece si ahorra al menos estos minutos. */
    private static final int AHORRO_MINIMO = 5;
    /** Número máximo de opciones devueltas. */
    private static final int MAX_OPCIONES = 5;
    /** Máximo de trenes por ruta. En la red actual bastan 4 (3 transbordos). */
    private static final int MAX_TRENES = 6;
    /** Límite de pasos de la búsqueda en profundidad, para acotar el tiempo de respuesta. */
    private static final int MAX_PASOS_BUSQUEDA = 500_000;
    private static final int SIN_CAMINO = Integer.MAX_VALUE / 2;

    private static final Linea A_PIE = new Linea("TRANS", "A pie", "Transbordo a pie", "000000");

    private final RedTransporte red;
    private List<Recorrido> recorridos;                      // se construyen en la primera búsqueda
    private Map<String, List<int[]>> recorridosPorEstacion;  // estación -> {recorrido, posición}

    /** Secuencia de estaciones de una línea, con los minutos desde la salida de la primera. */
    private static final class Recorrido {
        final Linea linea;
        final String[] estaciones;
        final int[] llegada;
        final int[] salida;

        Recorrido(Linea linea, String[] estaciones, int[] llegada, int[] salida) {
            this.linea = linea;
            this.estaciones = estaciones;
            this.llegada = llegada;
            this.salida = salida;
        }
    }

    /** Tramo de una ruta en construcción: en tren (recorrido >= 0) o a pie (recorrido = -1). */
    private record Paso(int recorrido, String desde, String hasta, int minutos) {}

    /** Estado de una búsqueda concreta. */
    private final class Busqueda {
        final String destino;
        final Map<String, Integer> trenesHasta;
        final int limiteTrenes;
        final Map<String, List<Paso>> mejorPorLineas = new HashMap<>();
        final Map<String, Integer> tiempoPorLineas = new HashMap<>();
        final Map<String, Integer> trenesPorLineas = new HashMap<>();
        final Deque<Paso> camino = new ArrayDeque<>();
        final Set<String> visitadas = new HashSet<>();
        /** Mejor tiempo con que se ha llegado a (estación, tramos usados, última línea). */
        final Map<String, Integer> mejorEstado = new HashMap<>();
        int pasos = 0;

        Busqueda(String destino, Map<String, Integer> trenesHasta, int limiteTrenes) {
            this.destino = destino;
            this.trenesHasta = trenesHasta;
            this.limiteTrenes = limiteTrenes;
        }

        void explorar(String x, int trenes, int minutos, int ultimoRecorrido, boolean vieneAPie) {
            if (++pasos > MAX_PASOS_BUSQUEDA) return;

            // En tren
            int espera = (trenes == 0) ? 0 : MINUTOS_TRANSBORDO;
            for (int[] rp : recorridosPorEstacion.getOrDefault(x, List.of())) {
                if (rp[0] == ultimoRecorrido) continue; // bajarse y volver a subir al mismo tren no aporta nada
                Recorrido r = recorridos.get(rp[0]);
                int i = rp[1];
                for (int j = i + 1; j < r.estaciones.length; j++) {
                    String y = r.estaciones[j];
                    if (visitadas.contains(y)) continue;
                    if (trenes + 1 + trenesHasta.getOrDefault(y, SIN_CAMINO) > limiteTrenes) continue;
                    int t = minutos + espera + r.llegada[j] - r.salida[i];
                    avanzar(new Paso(rp[0], x, y, r.llegada[j] - r.salida[i]), y, trenes + 1, t, rp[0], false);
                }
            }

            // A pie (no se encadenan dos tramos a pie)
            if (!vieneAPie) {
                for (Transbordo tb : red.getTransbordosDesde(x)) {
                    String y = tb.getToStopId();
                    if (y.equals(x) || visitadas.contains(y) || red.getEstacion(y) == null) continue;
                    if (trenes + 1 + trenesHasta.getOrDefault(y, SIN_CAMINO) > limiteTrenes) continue;
                    int andando = Math.max(1, tb.getMinTransferTime() / 60);
                    avanzar(new Paso(-1, x, y, andando), y, trenes + 1, minutos + andando, -1, true);
                }
            }
        }

        private void avanzar(Paso paso, String y, int trenes, int minutos, int recorrido, boolean aPie) {
            // Si ya se llegó a y con los mismos tramos y la misma última línea en menos o igual
            // tiempo, este camino no puede dar nada mejor: se descarta.
            String linea = aPie ? "a pie" : recorridos.get(recorrido).linea.getShortName();
            String estado = y + "|" + trenes + "|" + linea;
            Integer mejor = mejorEstado.get(estado);
            if (mejor != null && mejor <= minutos && !y.equals(destino)) return;
            mejorEstado.put(estado, mejor == null ? minutos : Math.min(mejor, minutos));

            camino.addLast(paso);
            visitadas.add(y);
            if (y.equals(destino)) {
                registrar(trenes, minutos);
            } else {
                explorar(y, trenes, minutos, recorrido, aPie);
            }
            visitadas.remove(y);
            camino.removeLast();
        }

        /** Guarda la ruta actual si es la más rápida con su secuencia de líneas. */
        private void registrar(int trenes, int minutos) {
            StringBuilder clave = new StringBuilder();
            for (Paso p : camino) {
                clave.append(p.recorrido() < 0 ? "a pie" : recorridos.get(p.recorrido()).linea.getShortName()).append('>');
            }
            String k = clave.toString();
            Integer previo = tiempoPorLineas.get(k);
            if (previo == null || minutos < previo) {
                tiempoPorLineas.put(k, minutos);
                trenesPorLineas.put(k, trenes);
                mejorPorLineas.put(k, new ArrayList<>(camino));
            }
        }
    }

    public ServicioBuscadorRutas(RedTransporte red) {
        this.red = red;
    }

 // 1. Sobrecarga para mantener compatibilidad si no se pasa una hora
    public List<Ruta> buscarRutas(Estacion origen, Estacion destino) {
        return buscarRutas(origen, destino, LocalTime.now());
    }

    // 2. Método principal modificado para recibir LocalTime horaSalida
    public List<Ruta> buscarRutas(Estacion origen, Estacion destino, LocalTime horaSalida) {
        List<Ruta> opciones = new ArrayList<>();
        if (origen == null || destino == null || origen.getId().equals(destino.getId())) return opciones;
        construirRecorridos();

        Map<String, Integer> trenesHasta = calcularTrenesHasta(destino.getId());
        Integer minimoTrenes = trenesHasta.get(origen.getId());
        if (minimoTrenes == null) return opciones; // no hay forma de llegar
        int minimo = minimoTrenes;

        Busqueda b = new Busqueda(destino.getId(), trenesHasta, Math.min(minimo + 1, MAX_TRENES));
        b.visitadas.add(origen.getId());
        b.explorar(origen.getId(), 0, 0, -1, false);

        // Mejor tiempo entre las rutas con el mínimo de trenes
        int mejorTiempoMinimo = Integer.MAX_VALUE;
        for (String k : b.mejorPorLineas.keySet()) {
            if (b.trenesPorLineas.get(k) == minimo) {
                mejorTiempoMinimo = Math.min(mejorTiempoMinimo, b.tiempoPorLineas.get(k));
            }
        }

        List<String> elegidas = new ArrayList<>();
        for (String k : b.mejorPorLineas.keySet()) {
            boolean conMinimo = b.trenesPorLineas.get(k) == minimo;
            if (conMinimo || b.tiempoPorLineas.get(k) <= mejorTiempoMinimo - AHORRO_MINIMO) {
                elegidas.add(k);
            }
        }
        // Las más rápidas primero; a igual tiempo, menos tramos
        elegidas.sort(Comparator.comparingInt((String k) -> b.tiempoPorLineas.get(k))
                .thenComparingInt(k -> b.trenesPorLineas.get(k))
                .thenComparing(k -> k)); // desempate fijo, para que el orden no dependa del HashMap

        for (String k : elegidas.subList(0, Math.min(MAX_OPCIONES, elegidas.size()))) {
            // Se pasa la horaSalida al constructor de la ruta
            opciones.add(construirRuta(b.mejorPorLineas.get(k), horaSalida)); 
        }
        return opciones;
    }

    // 3. Método construirRuta modificado para usar horaSalida en lugar de LocalTime.now()
    private Ruta construirRuta(List<Paso> pasos, LocalTime horaSalida) {
        Ruta ruta = new Ruta();
        LocalTime hora = horaSalida.withSecond(0).withNano(0);
        boolean yaEnTren = false;
        for (Paso p : pasos) {
            Estacion desde = red.getEstacion(p.desde());
            Estacion hasta = red.getEstacion(p.hasta());
            if (p.recorrido() < 0) {
                LocalTime llegada = hora.plusMinutes(p.minutos());
                ruta.agregarTramo(new Tramo(desde, hasta, A_PIE, hora, llegada, 0));
                hora = llegada;
            } else {
                if (yaEnTren) hora = hora.plusMinutes(MINUTOS_TRANSBORDO);
                Recorrido r = recorridos.get(p.recorrido());
                LocalTime llegada = hora.plusMinutes(p.minutos());
                double dist = desde.distanciaEnKm(hasta.getLatitud(), hasta.getLongitud());
                ruta.agregarTramo(new Tramo(desde, hasta, r.linea, hora, llegada, calcularPrecio(dist)));
                hora = llegada;
                yaEnTren = true;
            }
        }
        return ruta;
    }

    /**
     * Mínimo de tramos (trenes o transbordos a pie) desde cada estación hasta el destino, por
     * rondas hacia atrás: en la ronda k se marcan las estaciones desde las que un recorrido, o un
     * transbordo a pie, lleva a una estación marcada en una ronda anterior.
     */
    private Map<String, Integer> calcularTrenesHasta(String destino) {
        Map<String, Integer> dist = new HashMap<>();
        dist.put(destino, 0);
        for (int k = 1; k <= MAX_TRENES; k++) {
            Map<String, Integer> nuevas = new HashMap<>();
            for (Recorrido r : recorridos) {
                boolean llegaAMarcada = false;
                for (int j = r.estaciones.length - 1; j >= 0; j--) {
                    String e = r.estaciones[j];
                    Integer d = dist.get(e);
                    if (llegaAMarcada && d == null) nuevas.put(e, k);
                    if (d != null && d <= k - 1) llegaAMarcada = true;
                }
            }
            for (Estacion e : red.getAllEstaciones()) {
                if (dist.containsKey(e.getId())) continue;
                for (Transbordo t : red.getTransbordosDesde(e.getId())) {
                    Integer d = dist.get(t.getToStopId());
                    if (d != null && d <= k - 1) nuevas.put(e.getId(), k);
                }
            }
            if (nuevas.isEmpty()) break;
            dist.putAll(nuevas);
        }
        return dist;
    }

    

    /**
     * Agrupa los viajes en recorridos (línea comercial + secuencia de estaciones) y crea el índice
     * estación -> recorridos que salen de ella. La línea se identifica por su nombre comercial
     * porque en el GTFS de Renfe cada sentido de una línea tiene un route_id distinto.
     */
    private void construirRecorridos() {
        if (recorridos != null) return;
        Map<String, Recorrido> porClave = new LinkedHashMap<>();
        for (Viaje v : red.getAllViajes()) {
            Linea linea = red.getLinea(v.getRouteId());
            List<ParadaHorario> paradas = new ArrayList<>(red.getRutaDeViaje(v.getId()));
            if (linea == null || paradas.size() < 2) continue;
            paradas.sort(Comparator.comparingInt(ParadaHorario::getStopSequence));

            String[] estaciones = new String[paradas.size()];
            for (int i = 0; i < estaciones.length; i++) estaciones[i] = paradas.get(i).getStopId();
            String clave = linea.getShortName() + "|" + String.join(",", estaciones);
            if (porClave.containsKey(clave)) continue;

            int inicio = paradas.get(0).getDepartureTime();
            int[] llegada = new int[estaciones.length];
            int[] salida = new int[estaciones.length];
            for (int i = 0; i < estaciones.length; i++) {
                llegada[i] = (paradas.get(i).getArrivalTime() - inicio) / 60;
                salida[i] = (paradas.get(i).getDepartureTime() - inicio) / 60;
            }
            porClave.put(clave, new Recorrido(linea, estaciones, llegada, salida));
        }

        recorridos = new ArrayList<>(porClave.values());
        recorridosPorEstacion = new HashMap<>();
        for (int r = 0; r < recorridos.size(); r++) {
            String[] est = recorridos.get(r).estaciones;
            for (int i = 0; i < est.length - 1; i++) {
                recorridosPorEstacion.computeIfAbsent(est[i], k -> new ArrayList<>()).add(new int[]{r, i});
            }
        }
    }

    private double calcularPrecio(double distancia) {
        return Math.max(1.70, Math.min(1.70 + (distancia * 0.05), 5.50));
    }
}
