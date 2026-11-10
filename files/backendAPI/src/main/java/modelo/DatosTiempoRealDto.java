package modelo;

import java.util.List;

public class DatosTiempoRealDto {
    private List<EstadoViajeDto> viajes;

    public List<EstadoViajeDto> getViajes() {
        return viajes;
    }

    public void setViajes(List<EstadoViajeDto> viajes) {
        this.viajes = viajes;
    }

    public static class EstadoViajeDto {
        private String tripId;
        private int retrasoSegundos;
        private double latitud;
        private double longitud;

        public String getTripId() { return tripId; }
        public void setTripId(String tripId) { this.tripId = tripId; }
        public int getRetrasoSegundos() { return retrasoSegundos; }
        public void setRetrasoSegundos(int retrasoSegundos) { this.retrasoSegundos = retrasoSegundos; }
        public double getLatitud() { return latitud; }
        public void setLatitud(double latitud) { this.latitud = latitud; }
        public double getLongitud() { return longitud; }
        public void setLongitud(double longitud) { this.longitud = longitud; }
    }
}