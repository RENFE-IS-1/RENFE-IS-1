package modelo;

import java.time.LocalDateTime;

/**
 * Paso de un tren por una estación según el horario planificado (US-004).
 *
 * @param hora        fecha y hora prevista de llegada a la estación
 * @param linea       línea comercial del tren (C1, C3, C4a...)
 * @param destino     última estación del viaje, que indica el sentido de circulación
 * @param terminaAqui true si el tren finaliza su recorrido en esta estación
 * @param tripId      identificador GTFS del viaje
 */
public record Llegada(LocalDateTime hora, Linea linea, Estacion destino, boolean terminaAqui, String tripId) {}
