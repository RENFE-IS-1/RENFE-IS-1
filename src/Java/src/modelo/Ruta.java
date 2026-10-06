package modelo;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class Ruta {
    private List<Tramo> tramos;

    public Ruta() {
        this.tramos = new ArrayList<>();
    }

    public void agregarTramo(Tramo tramo) {
        this.tramos.add(tramo);
    }

    public List<Tramo> getTramos() { return tramos; }

    public double getPrecioTotal() {
        return tramos.stream().mapToDouble(Tramo::getPrecio).sum();
    }

    public long getTiempoTotalMinutos() {
        if (tramos.isEmpty()) return 0;
        LocalTime salidaRuta = tramos.get(0).getHoraSalida();
        LocalTime llegadaRuta = tramos.get(tramos.size() - 1).getHoraLlegada();

        if (llegadaRuta.isBefore(salidaRuta)) {
            return Duration.between(salidaRuta, llegadaRuta).plusDays(1).toMinutes();
        }
        return Duration.between(salidaRuta, llegadaRuta).toMinutes();
    }

    public int getNumeroTransbordos() {
        return Math.max(0, tramos.size() - 1);
    }

    @Override
    public String toString() {
        return String.format("Ruta [Duración: %d min | Precio: %.2f€ | Transbordos: %d]",
                getTiempoTotalMinutos(), getPrecioTotal(), getNumeroTransbordos());
    }
}