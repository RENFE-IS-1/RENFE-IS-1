package modelo;

/**
 * Retraso de un tren en una parada, tal como llega en un TripUpdate del GTFS-RT de Renfe.
 *
 * @param stopId           parada (stop_id), o null si la actualización no la indica
 * @param stopSequence     orden de la parada en el viaje (stop_sequence), o null si no viene
 * @param retrasoSegundos  retraso en segundos (negativo si va adelantado), o null si Renfe indica
 *                         que no tiene datos a partir de esta parada (NO_DATA)
 */
public record RetrasoParada(String stopId, Integer stopSequence, Integer retrasoSegundos) {

    /** Retraso del viaje entero, para cuando Renfe solo da un retraso sin indicar parada. */
    public static RetrasoParada deTodoElViaje(int retrasoSegundos) {
        return new RetrasoParada(null, null, retrasoSegundos);
    }

    public boolean esDeTodoElViaje() {
        return stopId == null && stopSequence == null;
    }
}
