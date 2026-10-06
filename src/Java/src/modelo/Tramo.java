package modelo;

import java.time.Duration;
import java.time.LocalTime;

public class Tramo {
    private Estacion origen;
    private Estacion destino;
    private Linea linea;
    private LocalTime horaSalida;
    private LocalTime horaLlegada;
    private double precio;

    public Tramo(Estacion origen, Estacion destino, Linea linea, LocalTime horaSalida, LocalTime horaLlegada, double precio) {
        this.origen = origen;
        this.destino = destino;
        this.linea = linea;
        this.horaSalida = horaSalida;
        this.horaLlegada = horaLlegada;
        this.precio = precio;
    }

    public Estacion getOrigen() { return origen; }
    public Estacion getDestino() { return destino; }
    public Linea getLinea() { return linea; }
    public LocalTime getHoraSalida() { return horaSalida; }
    public LocalTime getHoraLlegada() { return horaLlegada; }
    public double getPrecio() { return precio; }

    public long getDuracionMinutos() {
        if (horaLlegada.isBefore(horaSalida)) {
            return Duration.between(horaSalida, horaLlegada).plusDays(1).toMinutes();
        }
        return Duration.between(horaSalida, horaLlegada).toMinutes();
    }
}