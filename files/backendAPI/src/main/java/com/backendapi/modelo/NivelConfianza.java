package com.backendapi.modelo;

/**
 * Nivel de confianza de una incidencia declarada por viajeros, según cuántos viajeros distintos la
 * han declarado. Cuantas más personas declaran lo mismo en una línea, más fiable es la incidencia.
 *
 * - BAJA: 1 viajero.
 * - MEDIA: 2 viajeros.
 * - ALTA: VIAJEROS_ALTA o más viajeros; se considera confirmada.
 */
public enum NivelConfianza {
    BAJA,
    MEDIA,
    ALTA;

    /** Viajeros distintos necesarios para el nivel MEDIA. */
    public static final int VIAJEROS_MEDIA = 2;

    /** Viajeros distintos necesarios para el nivel ALTA (incidencia confirmada). */
    public static final int VIAJEROS_ALTA = 3;

    /** Calcula el nivel a partir del número de viajeros; lo necesitamos para dar valor a la acumulación de declaraciones. */
    public static NivelConfianza desde(int viajeros) {
        if (viajeros >= VIAJEROS_ALTA) return ALTA;
        if (viajeros >= VIAJEROS_MEDIA) return MEDIA;
        return BAJA;
    }
}
