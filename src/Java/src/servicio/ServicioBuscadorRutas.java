package servicio;

import modelo.Estacion;
import modelo.Linea;
import modelo.ParadaHorario;
import modelo.Ruta;
import modelo.Tramo;
import modelo.Transbordo;
import repositorio.RedTransporte;

import java.time.LocalTime;
import java.util.*;

public class ServicioBuscadorRutas {
    private final RedTransporte red;

    public ServicioBuscadorRutas(RedTransporte red) {
        this.red = red;
    }

    public List<Ruta> buscarRutas(Estacion origen, Estacion destino) {
        List<Ruta> opciones = new ArrayList<>();
        if (origen.getId().equals(destino.getId())) return opciones;

        // Estructuras para el algoritmo BFS en el grafo de estaciones
        Map<String, String> anteriorEstacion = new HashMap<>();
        Map<String, Linea> lineaUsada = new HashMap<>();
        Queue<String> cola = new LinkedList<>();
        Set<String> visitados = new HashSet<>();

        cola.add(origen.getId());
        visitados.add(origen.getId());

        boolean encontrado = false;

        while (!cola.isEmpty()) {
            String actualId = cola.poll();

            if (actualId.equals(destino.getId())) {
                encontrado = true;
                break;
            }

            // 1. Explorar vecinos a través de los trayectos de los trenes (tripId)
            List<ParadaHorario> horarios = red.getHorariosEstacion(actualId);
            Set<String> tripsRevisados = new HashSet<>();
            for (ParadaHorario ph : horarios) {
                String tripId = ph.getTripId();
                if (tripsRevisados.contains(tripId)) continue;
                tripsRevisados.add(tripId);

                List<ParadaHorario> rutaViaje = red.getRutaDeViaje(tripId);
                boolean pasar = false;
                for (ParadaHorario paradaTrip : rutaViaje) {
                    if (paradaTrip.getStopId().equals(actualId)) {
                        pasar = true;
                        continue;
                    }
                    if (pasar) {
                        String siguienteId = paradaTrip.getStopId();
                        if (!visitados.contains(siguienteId)) {
                            visitados.add(siguienteId);
                            anteriorEstacion.put(siguienteId, actualId);
                            var viaje = red.getViaje(tripId);
                            if (viaje != null) {
                                Linea l = red.getLinea(viaje.getRouteId());
                                lineaUsada.put(siguienteId, l);
                            }
                            cola.add(siguienteId);
                        }
                    }
                }
            }

            // 2. Explorar vecinos a través de transbordos peatonales oficiales (transfers.txt)
            List<Transbordo> transbordos = red.getTransbordosDesde(actualId);
            for (Transbordo t : transbordos) {
                String siguienteId = t.getToStopId();
                if (!visitados.contains(siguienteId)) {
                    visitados.add(siguienteId);
                    anteriorEstacion.put(siguienteId, actualId);
                    lineaUsada.put(siguienteId, null); // Transbordo a pie
                    cola.add(siguienteId);
                }
            }
        }

        if (!encontrado) {
            return opciones;
        }

        // Reconstruir la secuencia de estaciones desde el destino hasta el origen
        List<String> caminoIds = new ArrayList<>();
        String actual = destino.getId();
        while (actual != null) {
            caminoIds.add(0, actual);
            actual = anteriorEstacion.get(actual);
        }

        // Construir la ruta final con sus tramos
        Ruta r = new Ruta();
        LocalTime horaBase = LocalTime.now();
        LocalTime horaActual = horaBase;

        for (int i = 0; i < caminoIds.size() - 1; i++) {
            String estActualId = caminoIds.get(i);
            String estSiguienteId = caminoIds.get(i + 1);

            Estacion eActual = red.getEstacion(estActualId);
            Estacion eSiguiente = red.getEstacion(estSiguienteId);
            Linea l = lineaUsada.get(estSiguienteId);

            if (l == null) {
                l = new Linea("TRANS", "A pie", "Transbordo a pie", "000000");
            }

            double dist = eActual.distanciaEnKm(eSiguiente.getLatitud(), eSiguiente.getLongitud());
            long duracion = (long) Math.max(2, (dist * 1.33) + 2);
            double precio = calcularPrecio(dist);

            LocalTime horaLlegada = horaActual.plusMinutes(duracion);
            r.agregarTramo(new Tramo(eActual, eSiguiente, l, horaActual, horaLlegada, precio));
            horaActual = horaLlegada.plusMinutes(2);
        }

        opciones.add(r);
        return opciones;
    }

    private double calcularPrecio(double distancia) {
        return Math.max(1.70, Math.min(1.70 + (distancia * 0.05), 5.50));
    }
}