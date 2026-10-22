package modelo;

public class Estacion {
    private String id;
    private String nombre;
    private double latitud;
    private double longitud;
    private boolean activa;

    public Estacion(String id, String nombre, double latitud, double longitud) {
        this.id = id;
        this.nombre = nombre;
        this.latitud = latitud;
        this.longitud = longitud;
        this.activa = true;
    }

    public String getId() { return id; }
    public String getNombre() { return nombre; }
    public double getLatitud() { return latitud; }
    public double getLongitud() { return longitud; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }

    public double distanciaEnKm(double lat, double lon) {
        double dLat = Math.toRadians(lat - this.latitud);
        double dLon = Math.toRadians(lon - this.longitud);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(this.latitud)) * Math.cos(Math.toRadians(lat)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }
}