package modelo;

import java.util.Set;

/**
 * Aviso oficial de incidencia publicado por Renfe en su feed de alertas (GTFS-RT), ya limpio.
 *
 * Lo rellena ClienteApiRenfe en cada descarga y lo usa ServicioIncidencias (US-017, criterio 1) para
 * registrar los avisos como incidencias de las líneas afectadas. Separa el formato del JSON de Renfe
 * de la lógica de incidencias.
 *
 * @param id          identificador del aviso en el feed, p. ej. "AVISO_528848"
 * @param titulo      título del aviso, o null (Renfe no suele mandarlo)
 * @param descripcion texto del aviso, o null
 * @param routeIds    rutas afectadas (route_id de GTFS, p. ej. "10T0007C3")
 * @param stopIds     estaciones afectadas (stop_id), para los avisos que no indican rutas
 * @param inicioEpoch inicio del aviso en segundos desde 1970 (UTC), o null si no viene
 */
public record AvisoOficial(String id, String titulo, String descripcion,
                           Set<String> routeIds, Set<String> stopIds, Long inicioEpoch) {

    /** Copia los conjuntos para que el aviso no se pueda modificar después de crearlo. */
    public AvisoOficial {
        routeIds = routeIds == null ? Set.of() : Set.copyOf(routeIds);
        stopIds = stopIds == null ? Set.of() : Set.copyOf(stopIds);
    }
}
