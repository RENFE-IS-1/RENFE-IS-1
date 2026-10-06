package modelo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Calendario de un servicio GTFS (fila de calendar.txt más las excepciones de calendar_dates.txt).
 * Indica en qué fechas circulan los viajes que tienen este service_id.
 */
public class ServicioCalendario {
    private final String serviceId;
    private final boolean[] dias; // índice 0 = lunes ... 6 = domingo
    private final LocalDate inicio;
    private final LocalDate fin;
    private final Set<LocalDate> fechasAnadidas = new HashSet<>();
    private final Set<LocalDate> fechasEliminadas = new HashSet<>();

    public ServicioCalendario(String serviceId, boolean[] dias, LocalDate inicio, LocalDate fin) {
        this.serviceId = serviceId;
        this.dias = dias;
        this.inicio = inicio;
        this.fin = fin;
    }

    /** Servicio definido solo mediante excepciones de calendar_dates.txt. */
    public static ServicioCalendario soloExcepciones(String serviceId) {
        return new ServicioCalendario(serviceId, new boolean[7], null, null);
    }

    /** exceptionType: 1 = el servicio se añade esa fecha, 2 = se elimina (según GTFS). */
    public void addExcepcion(LocalDate fecha, int exceptionType) {
        if (exceptionType == 1) {
            fechasAnadidas.add(fecha);
            fechasEliminadas.remove(fecha);
        } else if (exceptionType == 2) {
            fechasEliminadas.add(fecha);
            fechasAnadidas.remove(fecha);
        }
    }

    public boolean activoEn(LocalDate fecha) {
        if (fechasAnadidas.contains(fecha)) return true;
        if (fechasEliminadas.contains(fecha)) return false;
        if (inicio == null || fin == null) return false;
        if (fecha.isBefore(inicio) || fecha.isAfter(fin)) return false;
        DayOfWeek dia = fecha.getDayOfWeek();
        return dias[dia.getValue() - 1];
    }

    public String getServiceId() { return serviceId; }
    public LocalDate getInicio() { return inicio; }
    public LocalDate getFin() { return fin; }
}
