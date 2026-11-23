package com.backendapi.servicio;

import com.backendapi.modelo.Incidencia;
import com.backendapi.modelo.OrigenIncidencia;
import com.backendapi.repositorio.IncidenciaRepository;
import modelo.Linea;
import modelo.Viaje;
import repositorio.RedTransporte;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detección de incidencias en las líneas (US-017).
 *
 * Parte 1: detección propia por acumulación de retrasos. Cuando al menos MIN_TRENES_RETRASADOS
 * trenes de una misma línea llevan MIN_RETRASO_SEGUNDOS o más de retraso, se registra una
 * incidencia por retraso en esa línea (criterio 2). Si se sigue detectando no se duplica
 * (criterio 3) y, cuando deja de detectarse, se cierra con su hora de fin (criterio 4).
 */
@Service
public class ServicioIncidencias {

    /** Número mínimo de trenes retrasados en una línea para registrar una incidencia (criterio 2). */
    static final int MIN_TRENES_RETRASADOS = 3;

    /** Retraso mínimo de un tren, en segundos, para contarlo como retrasado: 10 minutos (criterio 2). */
    static final int MIN_RETRASO_SEGUNDOS = 600;

    /** Tipo que se guarda en las incidencias detectadas por retrasos. */
    static final String TIPO_RETRASO = "RETRASO";

    /** Nombre comercial de una línea de Cercanías dentro de un trip_id, p. ej. "C3" o "C4a". */
    private static final Pattern PATRON_LINEA = Pattern.compile("(C\\d+[a-z]?)", Pattern.CASE_INSENSITIVE);

    private final IncidenciaRepository repositorio;
    private final RedTransporte red;

    /** Recibe el repositorio de incidencias y la red con los retrasos en tiempo real (Spring los inyecta). */
    public ServicioIncidencias(IncidenciaRepository repositorio, RedTransporte red) {
        this.repositorio = repositorio;
        this.red = red;
    }

    /**
     * Revisa los retrasos de la última descarga y crea, actualiza o cierra las incidencias por retraso.
     * Lo necesitamos para cumplir los criterios 2, 3 y 4 de US-017; se llamará en cada actualización
     * de los datos en tiempo real.
     *
     * Si la última descarga de retrasos ha fallado no se cambia nada: no saber si hay retrasos no
     * significa que la incidencia haya terminado, y no queremos cerrarla por un fallo de la API.
     *
     * @param ahora momento de la revisión; se guarda como hora de inicio, de actualización o de fin
     */
    @Transactional
    public void detectarIncidenciasPorRetraso(LocalDateTime ahora) {
        if (!red.isHorariosTiempoRealActivos()) return;

        Map<String, Integer> retrasadosPorLinea = contarTrenesRetrasadosPorLinea();

        // 1. Crear o actualizar la incidencia de cada línea que cumple la condición.
        for (Map.Entry<String, Integer> entrada : retrasadosPorLinea.entrySet()) {
            String linea = entrada.getKey();
            int trenes = entrada.getValue();
            if (trenes < MIN_TRENES_RETRASADOS) continue;

            String descripcion = descripcionRetraso(trenes);
            Optional<Incidencia> existente =
                    repositorio.findByLineaAndOrigenAndFinIsNull(linea, OrigenIncidencia.DETECCION_PROPIA);
            if (existente.isPresent()) {
                Incidencia incidencia = existente.get();
                incidencia.registrarNuevaDeteccion(ahora, descripcion);
                repositorio.save(incidencia);
            } else {
                repositorio.save(new Incidencia(linea, TIPO_RETRASO, descripcion,
                        OrigenIncidencia.DETECCION_PROPIA, null, ahora));
            }
        }

        // 2. Cerrar las incidencias por retraso activas cuya línea ya no cumple la condición.
        List<Incidencia> activas = repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.DETECCION_PROPIA);
        for (Incidencia incidencia : activas) {
            int trenes = retrasadosPorLinea.getOrDefault(incidencia.getLinea(), 0);
            if (trenes < MIN_TRENES_RETRASADOS) {
                incidencia.finalizar(ahora);
                repositorio.save(incidencia);
            }
        }
    }

    /**
     * Cuenta, para cada línea, cuántos trenes llevan MIN_RETRASO_SEGUNDOS o más de retraso.
     * Lo necesitamos para decidir en qué líneas hay una incidencia por retraso.
     * El retraso de cada tren es el de su próxima parada con datos (getRetrasoViajeSegundos).
     * Los trenes cuya línea no se puede averiguar se ignoran.
     */
    private Map<String, Integer> contarTrenesRetrasadosPorLinea() {
        Map<String, Integer> contador = new HashMap<>();
        for (String tripId : red.getViajesConRetraso()) {
            if (red.getRetrasoViajeSegundos(tripId) < MIN_RETRASO_SEGUNDOS) continue;
            String linea = lineaDeViaje(tripId);
            if (linea != null) contador.merge(linea, 1, Integer::sum);
        }
        return contador;
    }

    /**
     * Averigua la línea (p. ej. "C3") de un tren a partir de su trip_id.
     * Lo necesitamos para agrupar por línea los trenes retrasados.
     * Primero se busca el viaje en los horarios GTFS cargados; si Renfe usa un trip_id que no está
     * en ellos, se intenta sacar el nombre de la línea del propio trip_id, igual que hace el mapa
     * de trenes (RutasController). Devuelve null si no se puede averiguar.
     */
    private String lineaDeViaje(String tripId) {
        Viaje viaje = red.getViaje(tripId);
        if (viaje != null) {
            Linea linea = red.getLinea(viaje.getRouteId());
            if (linea != null && linea.getShortName() != null && !linea.getShortName().isBlank()) {
                return linea.getShortName();
            }
        }
        Matcher m = PATRON_LINEA.matcher(tripId);
        return m.find() ? m.group(1).toUpperCase() : null;
    }

    /** Texto de la descripción de una incidencia por retraso; lo necesitamos para que el viajero sepa qué ocurre. */
    private static String descripcionRetraso(int trenes) {
        return trenes + " trenes acumulan un retraso de " + (MIN_RETRASO_SEGUNDOS / 60) + " minutos o más.";
    }
}
