package servicio;

import modelo.Estacion;
import modelo.Linea;
import modelo.ParadaHorario;
import modelo.Ruta;
import modelo.Tramo;
import modelo.Transbordo;
import modelo.Viaje;
import repositorio.RedTransporte;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
 *       queda la más rápida según los tiempos de viaje. Se toman hasta {@code MAX_CANDIDATAS}.</li>
 *   <li>A cada candidata se le asignan trenes reales que circulan ese día (calendario GTFS):
 *       primero los que dan la llegada más temprana y después, hacia atrás, los que salen más
 *       tarde sin retrasar esa llegada (ver {@link #programar}).</li>
 *   <li>Se ofrecen todas las de menos tramos y, con un tramo más, solo las que llegan al menos
 *       {@code AHORRO_MINIMO} minutos antes. Se ordenan por hora de llegada.</li>
 * </ol>
 */
public class ServicioBuscadorRutas {
    /** Tiempo de espera que se suma en cada transbordo entre trenes. */
    private static final int MINUTOS_TRANSBORDO = 2;
    /** Una ruta con un transbordo más solo se ofrece si ahorra al menos estos minutos. */
    private static final int AHORRO_MINIMO = 5;
    /** Número máximo de opciones devueltas. */
    private static final int MAX_OPCIONES = 5;
    /** Combinaciones de líneas con trenes reales entre las que se eligen las mejores. */
    private static final int MAX_CANDIDATAS = 15;
    /** Máximo de combinaciones a las que se intenta asignar trenes. */
    private static final int MAX_INTENTOS = 60;
    /** Se descartan las opciones que llegan más de esto después de la mejor. */
    private static final int MAX_RETRASO_MINUTOS = 60;
    /** Margen para la búsqueda binaria: un tren puede llegar a la estación hasta 1 h antes de salir. */
    private static final long MARGEN_PARADA_SEG = 3600;
    private static final ZoneId ZONA_MADRID = ZoneId.of("Europe/Madrid");
    /**
     * Espera máxima razonable en un transbordo. Tras la pasada hacia atrás, una espera mayor solo
     * aparece cuando el servicio cierra por la noche: entonces se propone salir más tarde.
     */
    public static final int MAX_ESPERA_MINUTOS = 120;
    /** Veces que se puede retrasar la salida para evitar una espera nocturna. */
    private static final int MAX_REINTENTOS = 5;
    /** Máximo de trenes por ruta. En la red actual bastan 4 (3 transbordos). */
    private static final int MAX_TRENES = 6;
    /** Límite de pasos de la búsqueda en profundidad, para acotar el tiempo de respuesta. */
    private static final int MAX_PASOS_BUSQUEDA = 500_000;
    private static final int SIN_CAMINO = Integer.MAX_VALUE / 2;

    private static final Linea A_PIE = new Linea("TRANS", "A pie", "Transbordo a pie", "000000");

    private final RedTransporte red;
    private List<Recorrido> recorridos;                      // se construyen en la primera búsqueda
    private Map<String, List<int[]>> recorridosPorEstacion;  // estación -> {recorrido, posición}
    /** estación -> línea comercial -> horarios de esa línea en la estación, ordenados por llegada. */
    private final Map<String, Map<String, List<ParadaHorario>>> horariosPorEstacionYLinea = new HashMap<>();

    /** Secuencia de estaciones de una línea, con los minutos desde la salida de la primera. */
    private static final class Recorrido {
        final Linea linea;
        final String[] estaciones;
        final int[] llegada;
        final int[] salida;
        /** service_id de los viajes de este recorrido: dicen qué días circula. */
        final Set<String> servicios = new HashSet<>();

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
        final boolean[] activo;
        final Map<String, List<Paso>> mejorPorLineas = new HashMap<>();
        final Map<String, Integer> tiempoPorLineas = new HashMap<>();
        final Map<String, Integer> trenesPorLineas = new HashMap<>();
        final Deque<Paso> camino = new ArrayDeque<>();
        final Set<String> visitadas = new HashSet<>();
        /** Mejor tiempo con que se ha llegado a (estación, tramos usados, última línea). */
        final Map<String, Integer> mejorEstado = new HashMap<>();
        int pasos = 0;

        Busqueda(String destino, Map<String, Integer> trenesHasta, int limiteTrenes, boolean[] activo) {
            this.activo = activo;
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
                if (!activo[rp[0]]) continue;          // ese recorrido no tiene trenes estos días
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

    /** Rutas saliendo ahora (hora de Madrid). */
    public List<Ruta> buscarRutas(Estacion origen, Estacion destino) {
        return buscarRutas(origen, destino, LocalDateTime.now(ZONA_MADRID));
    }

    /** Rutas saliendo hoy a la hora indicada. */
    public List<Ruta> buscarRutas(Estacion origen, Estacion destino, LocalTime horaSalida) {
        return buscarRutas(origen, destino, LocalDateTime.of(LocalDate.now(ZONA_MADRID), horaSalida));
    }

    /**
     * Rutas saliendo a partir de la fecha y hora indicadas, con los trenes reales que circulan ese
     * día. Se devuelven ordenadas por hora de llegada (la primera es la que llega antes).
     */
    public List<Ruta> buscarRutas(Estacion origen, Estacion destino, LocalDateTime salida) {
        List<Ruta> opciones = new ArrayList<>();
        if (origen == null || destino == null || salida == null
                || origen.getId().equals(destino.getId())) return opciones;
        construirRecorridos();

        // Recorridos con algún tren entre el día anterior (trenes que pasan de medianoche) y el
        // siguiente. Si con ellos no sale ninguna ruta (por ejemplo, un viernes por la noche con
        // recorridos de entre semana que el sábado ya no existen), se repite solo con los del día
        // siguiente.
        LocalDate dia = salida.toLocalDate();
        List<RutaProgramada> elegidas = buscarEnDias(origen, destino, salida, dia.minusDays(1), dia.plusDays(1));
        if (elegidas.isEmpty()) {
            elegidas = buscarEnDias(origen, destino, salida, dia.plusDays(1), dia.plusDays(1));
        }
        for (RutaProgramada rp : elegidas) opciones.add(rp.ruta());
        return opciones;
    }

    /** Búsqueda completa usando solo los recorridos que circulan entre esas dos fechas. */
    private List<RutaProgramada> buscarEnDias(Estacion origen, Estacion destino, LocalDateTime salida,
                                              LocalDate desdeDia, LocalDate hastaDia) {
        // 1. Combinaciones de líneas y estaciones de transbordo posibles (sin horarios)
        boolean[] activo = recorridosActivos(desdeDia, hastaDia);
        Map<String, Integer> trenesHasta = calcularTrenesHasta(destino.getId(), activo);
        Integer minimoTrenes = trenesHasta.get(origen.getId());
        if (minimoTrenes == null) return List.of(); // no hay forma de llegar
        int minimo = minimoTrenes;

        Busqueda b = new Busqueda(destino.getId(), trenesHasta, Math.min(minimo + 1, MAX_TRENES), activo);
        b.visitadas.add(origen.getId());
        b.explorar(origen.getId(), 0, 0, -1, false);

        List<String> candidatas = new ArrayList<>(b.mejorPorLineas.keySet());
        candidatas.sort(Comparator.comparingInt((String k) -> b.tiempoPorLineas.get(k))
                .thenComparingInt(k -> b.trenesPorLineas.get(k))
                .thenComparing(k -> k)); // desempate fijo, para que el orden no dependa del HashMap

        // 2. Se asignan trenes reales a las candidatas, de la más rápida a la más lenta, hasta tener
        //    MAX_CANDIDATAS con trenes (las que no tienen trenes a esa hora o ese día no cuentan)
        List<RutaProgramada> programadas = new ArrayList<>();
        for (int i = 0; i < candidatas.size() && i < MAX_INTENTOS && programadas.size() < MAX_CANDIDATAS; i++) {
            String k = candidatas.get(i);
            RutaProgramada rp = programar(b.mejorPorLineas.get(k), salida, b.trenesPorLineas.get(k), k);
            if (rp != null) programadas.add(rp);
        }
        if (programadas.isEmpty()) return List.of();

        // 3. Todas las de menos tramos; con un tramo más, solo si llegan claramente antes
        LocalDateTime mejorLlegadaMinimo = LocalDateTime.MAX;
        LocalDateTime mejorLlegada = LocalDateTime.MAX;
        for (RutaProgramada rp : programadas) {
            if (rp.tramos() == minimo && rp.llegada().isBefore(mejorLlegadaMinimo)) mejorLlegadaMinimo = rp.llegada();
            if (rp.llegada().isBefore(mejorLlegada)) mejorLlegada = rp.llegada();
        }
        List<RutaProgramada> elegidas = new ArrayList<>();
        for (RutaProgramada rp : programadas) {
            boolean aceptable = rp.tramos() == minimo || mejorLlegadaMinimo == LocalDateTime.MAX
                    || !rp.llegada().isAfter(mejorLlegadaMinimo.minusMinutes(AHORRO_MINIMO));
            // Una opción que llega mucho después que la mejor no es una alternativa real
            boolean cercana = !rp.llegada().isAfter(mejorLlegada.plusMinutes(MAX_RETRASO_MINUTOS));
            if (aceptable && cercana) elegidas.add(rp);
        }
        elegidas.sort(Comparator.comparing(RutaProgramada::llegada)
                .thenComparingInt(RutaProgramada::tramos)
                .thenComparing(RutaProgramada::clave));
        return elegidas.subList(0, Math.min(MAX_OPCIONES, elegidas.size()));
    }

    /** Qué recorridos tienen algún tren entre las dos fechas (incluidas). */
    private boolean[] recorridosActivos(LocalDate desde, LocalDate hasta) {
        boolean[] activo = new boolean[recorridos.size()];
        for (int i = 0; i < recorridos.size(); i++) {
            buscar:
            for (String servicio : recorridos.get(i).servicios) {
                for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
                    if (red.isServicioActivo(servicio, d)) { activo[i] = true; break buscar; }
                }
            }
        }
        return activo;
    }

    /** Ruta con trenes concretos, más los datos necesarios para ordenarla. */
    private record RutaProgramada(Ruta ruta, LocalDateTime llegada, int tramos, String clave) {}

    /** Tren concreto elegido para un tramo. */
    private record TrenElegido(LocalDateTime salida, LocalDateTime llegada, Linea linea) {}

    /**
     * Asigna trenes reales a la combinación en dos pasadas:
     * <ol>
     *   <li><b>Ida:</b> cada tramo toma el tren que llega antes, saliendo después de la hora pedida
     *       (o de la llegada del tramo anterior más {@code MINUTOS_TRANSBORDO}). Así se obtiene la
     *       hora de llegada más temprana posible con estas líneas.</li>
     *   <li><b>Vuelta:</b> desde el último tramo hacia el primero, cada uno se cambia por el tren que
     *       sale más tarde sin retrasar la llegada final. Evita esperas inútiles: de noche, en vez
     *       de coger el último tren y esperar horas a que abra el siguiente servicio, se propone
     *       salir con el primero de la mañana.</li>
     * </ol>
     * Si aun así queda una espera de más de {@code MAX_ESPERA_MINUTOS} en un transbordo (cierre
     * nocturno: coger el último tren y esperar horas al primero de la mañana), se vuelve a programar
     * saliendo después del primer tren elegido.
     *
     * <p>Devuelve null si algún tramo no tiene tren (fuera del periodo de los horarios cargados).
     */
    private RutaProgramada programar(List<Paso> pasos, LocalDateTime salida, int tramos, String clave) {
        return programar(pasos, salida, tramos, clave, MAX_REINTENTOS);
    }

    private RutaProgramada programar(List<Paso> pasos, LocalDateTime salida, int tramos, String clave, int reintentos) {
        int n = pasos.size();
        TrenElegido[] ida = new TrenElegido[n];
        LocalDateTime t = salida.withSecond(0).withNano(0);
        boolean yaEnTren = false;
        for (int i = 0; i < n; i++) {
            Paso p = pasos.get(i);
            if (p.recorrido() < 0) {
                ida[i] = new TrenElegido(t, t.plusMinutes(p.minutos()), A_PIE);
            } else {
                LocalDateTime listo = yaEnTren ? t.plusMinutes(MINUTOS_TRANSBORDO) : t;
                ida[i] = siguienteTren(lineaDe(p), p.desde(), p.hasta(), listo);
                if (ida[i] == null) return null;
                yaEnTren = true;
            }
            t = ida[i].llegada();
        }
        LocalDateTime llegadaFinal = t;

        TrenElegido[] elegidos = new TrenElegido[n];
        LocalDateTime limite = llegadaFinal;
        for (int i = n - 1; i >= 0; i--) {
            Paso p = pasos.get(i);
            if (p.recorrido() < 0) {
                elegidos[i] = new TrenElegido(limite.minusMinutes(p.minutos()), limite, A_PIE);
            } else {
                TrenElegido tarde = ultimoTren(lineaDe(p), p.desde(), p.hasta(), ida[i].salida(), limite);
                elegidos[i] = (tarde != null) ? tarde : ida[i]; // el de la ida siempre cumple
            }
            limite = elegidos[i].salida();
            if (p.recorrido() >= 0 && hayTrenAntes(pasos, i)) limite = limite.minusMinutes(MINUTOS_TRANSBORDO);
        }

        for (int i = 1; i < n; i++) {
            long espera = Duration.between(elegidos[i - 1].llegada(), elegidos[i].salida()).toMinutes();
            if (espera > MAX_ESPERA_MINUTOS) {
                // Nunca se ofrece una ruta con una espera así: se sale después o se descarta.
                // Se vuelve a salir justo a tiempo para el tren de después del hueco: su salida menos
                // lo que se tarda hasta llegar a esa estación y el tiempo de transbordo.
                if (reintentos == 0) return null;
                Duration hastaElHueco = Duration.between(elegidos[0].salida(), elegidos[i - 1].llegada());
                LocalDateTime nuevaSalida = elegidos[i].salida().minus(hastaElHueco).minusMinutes(MINUTOS_TRANSBORDO);
                if (!nuevaSalida.isAfter(elegidos[0].salida())) nuevaSalida = elegidos[0].salida().plusMinutes(1);
                return programar(pasos, nuevaSalida, tramos, clave, reintentos - 1);
            }
        }

        Ruta ruta = new Ruta();
        for (int i = 0; i < n; i++) {
            Paso p = pasos.get(i);
            Estacion desde = red.getEstacion(p.desde());
            Estacion hasta = red.getEstacion(p.hasta());
            double precio = 0;
            if (p.recorrido() >= 0) precio = calcularPrecio(desde.distanciaEnKm(hasta.getLatitud(), hasta.getLongitud()));
            ruta.agregarTramo(new Tramo(desde, hasta, elegidos[i].linea(), elegidos[i].salida(), elegidos[i].llegada(), precio));
        }
        return new RutaProgramada(ruta, llegadaFinal, tramos, clave);
    }

    private String lineaDe(Paso p) {
        return recorridos.get(p.recorrido()).linea.getShortName();
    }

    private static boolean hayTrenAntes(List<Paso> pasos, int i) {
        for (int j = 0; j < i; j++) if (pasos.get(j).recorrido() >= 0) return true;
        return false;
    }

    /**
     * Entre los trenes de la línea de {@code desde} a {@code hasta} que circulan ese día, salen no
     * antes de {@code noAntes} y llegan no después de {@code limite}, el que sale más tarde (a igual
     * salida, el que llega antes). Null si no hay ninguno.
     */
    private TrenElegido ultimoTren(String linea, String desde, String hasta, LocalDateTime noAntes, LocalDateTime limite) {
        List<ParadaHorario> horarios = horariosPorEstacionYLinea
                .getOrDefault(desde, Map.of()).getOrDefault(linea, List.of());
        TrenElegido mejor = null;
        for (LocalDate dia = noAntes.toLocalDate().minusDays(1); !dia.isAfter(limite.toLocalDate()); dia = dia.plusDays(1)) {
            LocalDateTime inicioDia = dia.atStartOfDay();
            long desdeSeg = Duration.between(inicioDia, noAntes).getSeconds();
            long hastaSeg = Duration.between(inicioDia, limite).getSeconds();
            // Se recorre hacia atrás desde la hora límite: el primer tren válido es casi siempre el
            // buscado, y se para cuando ya ninguno puede salir más tarde que el mejor encontrado.
            for (int i = primeraLlegadaDesde(horarios, hastaSeg + 1) - 1; i >= 0; i--) {
                ParadaHorario ph = horarios.get(i);
                if (ph.getArrivalTime() < desdeSeg - MARGEN_PARADA_SEG) break;
                if (mejor != null && inicioDia.plusSeconds(ph.getArrivalTime() + MARGEN_PARADA_SEG).isBefore(mejor.salida())) break;
                if (ph.getDepartureTime() < desdeSeg || ph.getDepartureTime() > hastaSeg) continue;

                Viaje v = red.getViaje(ph.getTripId());
                if (v == null) continue;
                Linea l = red.getLinea(v.getRouteId());
                if (l == null || !l.getShortName().equals(linea)) continue;
                if (!red.isServicioActivo(v.getServiceId(), dia)) continue;
                ParadaHorario bajada = paradaPosterior(v.getId(), ph.getStopSequence(), hasta);
                if (bajada == null || bajada.getArrivalTime() > hastaSeg) continue;

                LocalDateTime sal = inicioDia.plusSeconds(ph.getDepartureTime());
                LocalDateTime lleg = inicioDia.plusSeconds(bajada.getArrivalTime());
                if (mejor == null || sal.isAfter(mejor.salida())
                        || (sal.equals(mejor.salida()) && lleg.isBefore(mejor.llegada()))) {
                    mejor = new TrenElegido(sal, lleg, l);
                }
            }
        }
        return mejor;
    }

    /**
     * Entre los trenes de la línea que paran en {@code desde} y después en {@code hasta}, que
     * circulan ese día y salen a partir de {@code listo}, devuelve el que llega antes a {@code hasta}.
     *
     * <p>Como en las llegadas a una estación (US-004), las horas GTFS cuentan desde el inicio del
     * día de servicio y pueden pasar de las 24:00, así que se miran los días de servicio D-1, D y
     * D+1. Los horarios de la estación están ordenados por hora de llegada: se empieza por búsqueda
     * binaria y se para en cuanto un tren llega a {@code desde} después de la mejor llegada a
     * {@code hasta} encontrada, porque ninguno posterior puede mejorarla.
     */
    private TrenElegido siguienteTren(String linea, String desde, String hasta, LocalDateTime listo) {
        List<ParadaHorario> horarios = horariosPorEstacionYLinea
                .getOrDefault(desde, Map.of()).getOrDefault(linea, List.of());
        TrenElegido mejor = null;
        for (int desfase = -1; desfase <= 1; desfase++) {
            LocalDate diaServicio = listo.toLocalDate().plusDays(desfase);
            LocalDateTime inicioDia = diaServicio.atStartOfDay();
            long umbral = Duration.between(inicioDia, listo).getSeconds(); // segundos del día de servicio

            for (int i = primeraLlegadaDesde(horarios, umbral - MARGEN_PARADA_SEG); i < horarios.size(); i++) {
                ParadaHorario ph = horarios.get(i);
                if (mejor != null && !inicioDia.plusSeconds(ph.getArrivalTime()).isBefore(mejor.llegada())) break;
                if (ph.getDepartureTime() < umbral) continue;

                Viaje v = red.getViaje(ph.getTripId());
                if (v == null) continue;
                Linea l = red.getLinea(v.getRouteId());
                if (l == null || !l.getShortName().equals(linea)) continue;
                if (!red.isServicioActivo(v.getServiceId(), diaServicio)) continue;
                ParadaHorario bajada = paradaPosterior(v.getId(), ph.getStopSequence(), hasta);
                if (bajada == null) continue;

                LocalDateTime llegada = inicioDia.plusSeconds(bajada.getArrivalTime());
                if (mejor == null || llegada.isBefore(mejor.llegada())) {
                    mejor = new TrenElegido(inicioDia.plusSeconds(ph.getDepartureTime()), llegada, l);
                }
            }
        }
        return mejor;
    }

    /** Primera posición de la lista (ordenada por llegada) con llegada mayor o igual que {@code segundos}. */
    private static int primeraLlegadaDesde(List<ParadaHorario> horarios, long segundos) {
        int lo = 0, hi = horarios.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (horarios.get(mid).getArrivalTime() < segundos) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** Parada del viaje en {@code estacion} posterior a la secuencia dada, o null si no la hay. */
    private ParadaHorario paradaPosterior(String tripId, int secuencia, String estacion) {
        for (ParadaHorario ph : red.getRutaDeViaje(tripId)) {
            if (ph.getStopSequence() > secuencia && ph.getStopId().equals(estacion)) return ph;
        }
        return null;
    }

    /**
     * Mínimo de tramos (trenes o transbordos a pie) desde cada estación hasta el destino, por
     * rondas hacia atrás: en la ronda k se marcan las estaciones desde las que un recorrido, o un
     * transbordo a pie, lleva a una estación marcada en una ronda anterior.
     */
    private Map<String, Integer> calcularTrenesHasta(String destino, boolean[] activo) {
        Map<String, Integer> dist = new HashMap<>();
        dist.put(destino, 0);
        for (int k = 1; k <= MAX_TRENES; k++) {
            Map<String, Integer> nuevas = new HashMap<>();
            for (int ri = 0; ri < recorridos.size(); ri++) {
                if (!activo[ri]) continue;
                Recorrido r = recorridos.get(ri);
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
            for (ParadaHorario ph : paradas) {
                horariosPorEstacionYLinea.computeIfAbsent(ph.getStopId(), k -> new HashMap<>())
                        .computeIfAbsent(linea.getShortName(), k -> new ArrayList<>()).add(ph);
            }
            Recorrido existente = porClave.get(clave);
            if (existente != null) {
                existente.servicios.add(v.getServiceId());
                continue;
            }

            int inicio = paradas.get(0).getDepartureTime();
            int[] llegada = new int[estaciones.length];
            int[] salida = new int[estaciones.length];
            for (int i = 0; i < estaciones.length; i++) {
                llegada[i] = (paradas.get(i).getArrivalTime() - inicio) / 60;
                salida[i] = (paradas.get(i).getDepartureTime() - inicio) / 60;
            }
            Recorrido nuevo = new Recorrido(linea, estaciones, llegada, salida);
            nuevo.servicios.add(v.getServiceId());
            porClave.put(clave, nuevo);
        }

        recorridos = new ArrayList<>(porClave.values());
        for (Map<String, List<ParadaHorario>> porLinea : horariosPorEstacionYLinea.values()) {
            for (List<ParadaHorario> lista : porLinea.values()) lista.sort(Comparator.comparingInt(ParadaHorario::getArrivalTime));
        }

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
