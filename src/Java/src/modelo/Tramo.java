package modelo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class Tramo {
    private Estacion origen;
    private Estacion destino;
    private Linea linea;
    private LocalTime horaSalida;
    private LocalTime horaLlegada;
    private double precio;
    // Fecha y hora completas, cuando se conocen (tramos con trenes reales). Pueden ser null.
    private LocalDateTime fechaHoraSalida;
    private LocalDateTime fechaHoraLlegada;

    public Tramo(Estacion origen, Estacion destino, Linea linea, LocalTime horaSalida, LocalTime horaLlegada, double precio) {
        this.origen = origen;
        this.destino = destino;
        this.linea = linea;
        this.horaSalida = horaSalida;
        this.horaLlegada = horaLlegada;
        this.precio = precio;
    }

    /** Tramo con fecha: necesario cuando el tren sale otro día distinto al de la consulta. */
    public Tramo(Estacion origen, Estacion destino, Linea linea, LocalDateTime salida, LocalDateTime llegada, double precio) {
        this(origen, destino, linea, salida.toLocalTime(), llegada.toLocalTime(), precio);
        this.fechaHoraSalida = salida;
        this.fechaHoraLlegada = llegada;
    }

    public Estacion getOrigen() { return origen; }
    public Estacion getDestino() { return destino; }
    public Linea getLinea() { return linea; }
    public LocalTime getHoraSalida() { return horaSalida; }
    public LocalTime getHoraLlegada() { return horaLlegada; }
    public double getPrecio() { return precio; }
    public LocalDateTime getFechaHoraSalida() { return fechaHoraSalida; }
    public LocalDateTime getFechaHoraLlegada() { return fechaHoraLlegada; }

    public long getDuracionMinutos() {
        if (horaLlegada.isBefore(horaSalida)) {
            return Duration.between(horaSalida, horaLlegada).plusDays(1).toMinutes();
        }
        return Duration.between(horaSalida, horaLlegada).toMinutes();
    }
}