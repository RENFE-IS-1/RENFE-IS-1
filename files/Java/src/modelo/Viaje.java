package modelo;

public class Viaje {
    private String id;
    private String routeId;
    private String serviceId;
    private double latitudActual;
    private double longitudActual;

    public Viaje(String id, String routeId, String serviceId) {
        this.id = id;
        this.routeId = routeId;
        this.serviceId = serviceId;
    }

    public String getId() { return id; }
    public String getRouteId() { return routeId; }
    public String getServiceId() { return serviceId; }
    
    public void actualizarPosicion(double lat, double lon) {
        this.latitudActual = lat;
        this.longitudActual = lon;
    }
}