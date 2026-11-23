package com.backendapi.modelo;

/**
 * Origen de una incidencia (US-017, criterio 5).
 *
 * - AVISO_OFICIAL: aviso publicado por una fuente externa (feed de alertas GTFS-RT de Renfe).
 * - DETECCION_PROPIA: incidencia detectada por la aplicación al acumularse retrasos en una línea
 *   (al menos 3 trenes con 10 minutos o más de retraso).
 * - DECLARADA_POR_USUARIO: incidencia declarada por uno o varios viajeros desde la aplicación.
 */
public enum OrigenIncidencia {
    AVISO_OFICIAL,
    DETECCION_PROPIA,
    DECLARADA_POR_USUARIO
}
