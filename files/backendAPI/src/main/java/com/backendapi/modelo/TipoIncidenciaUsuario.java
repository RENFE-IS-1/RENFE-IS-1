package com.backendapi.modelo;

/**
 * Tipos de incidencia que un viajero puede declarar desde la aplicación.
 *
 * Es una lista cerrada para poder agrupar las declaraciones de varios viajeros (misma línea y mismo
 * tipo = misma incidencia). Si ningún tipo encaja se usa OTRO, que obliga a especificar el texto.
 */
public enum TipoIncidenciaUsuario {
    AVERIA_TREN("Avería en el tren"),
    TREN_PARADO("Tren detenido en la vía o en una estación"),
    RETRASO("Retraso"),
    AGLOMERACION("Aglomeración"),
    ESTACION_O_ACCESO_CERRADO("Estación o acceso cerrado"),
    ACCESIBILIDAD("Ascensores o escaleras fuera de servicio"),
    SEGURIDAD("Problema de seguridad"),
    OTRO("Otra incidencia");

    private final String etiqueta;

    /** Asocia a cada tipo el texto que se muestra al viajero. */
    TipoIncidenciaUsuario(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Texto legible del tipo; lo necesitamos para la descripción de la incidencia y el desplegable de la web. */
    public String getEtiqueta() {
        return etiqueta;
    }

    /** Indica si el tipo exige que el viajero escriba qué ocurre; lo necesitamos para validar las declaraciones de tipo OTRO. */
    public boolean requiereEspecificacion() {
        return this == OTRO;
    }
}
