package modelo;

import java.time.Duration;
import java.time.LocalDateTime;
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

    /** Fecha y hora de salida del primer tramo (null si los tramos no llevan fecha). */
    public LocalDateTime getFechaHoraSalida() {
        return tramos.isEmpty() ? null : tramos.get(0).getFechaHoraSalida();
    }

    /** Fecha y hora de llegada del último tramo (null si los tramos no llevan fecha). */
    public LocalDateTime getFechaHoraLlegada() {
        return tramos.isEmpty() ? null : tramos.get(tramos.size() - 1).getFechaHoraLlegada();
    }

    public long getTiempoTotalMinutos() {
        if (tramos.isEmpty()) return 0;
        if (getFechaHoraSalida() != null && getFechaHoraLlegada() != null) {
            return Duration.between(getFechaHoraSalida(), getFechaHoraLlegada()).toMinutes();
        }
        LocalTime salidaRuta = tramos.get(0).getHoraSalida();
        LocalTime llegadaRuta = tramos.get(tramos.size() - 1).getHoraLlegada();

        if (llegadaRuta.isBefore(salidaRuta)) {
            return Duration.between(salidaRuta, llegadaRuta).plusDays(1).toMinutes();
        }
        return Duration.between(salidaRuta, llegadaRuta).toMinutes();
    }

    /** Cambios de tren: los tramos a pie (línea "TRANS") no cuentan como tren. */
    public int getNumeroTransbordos() {
        long trenes = tramos.stream().filter(t -> !"TRANS".equals(t.getLinea().getId())).count();
        return (int) Math.max(0, trenes - 1);
    }

    @Override
    public String toString() {
        return String.format("Ruta [Duración: %d min | Precio: %.2f€ | Transbordos: %d]",
                getTiempoTotalMinutos(), getPrecioTotal(), getNumeroTransbordos());
    }
}