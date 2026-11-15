package modelo;

import java.time.LocalDateTime;
import java.time.LocalTime;

/** Datos de una llegada preparados para la respuesta REST. */
public record LlegadaDTO(
        String linea,
        String destino,
        LocalTime horaPlanificada,
        LocalTime horaEstimada,
        int retrasoMinutos,
        EstadoTren estado,
        boolean esTiempoReal,
        LocalDateTime ultimaActualizacion) {
}
