package modelo;

import java.time.LocalDateTime;

/**
 * Paso de un tren por una estación según el horario planificado (US-004) y tiempo real.
 */
public record Llegada(
    LocalDateTime hora, 
    Linea linea, 
    Estacion destino, 
    boolean terminaAqui, 
    String tripId,
    int retrasoMinutos,
    boolean tiempoReal,
    LocalDateTime ultimaActualizacion
) {
    // Constructor de compatibilidad para no romper las llamadas existentes
    public Llegada(LocalDateTime hora, Linea linea, Estacion destino, boolean terminaAqui, String tripId) {
        this(hora, linea, destino, terminaAqui, tripId, 0, false, null);
    }
}