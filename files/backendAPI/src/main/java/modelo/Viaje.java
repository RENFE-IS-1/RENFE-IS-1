package modelo;

public class Viaje {
    private String id;
    private String routeId;
    private String serviceId;
    /** block_id del GTFS: viajes del mismo bloque son el mismo tren físico, uno detrás de otro. */
    private String blockId;
    private double latitudActual;
    private double longitudActual;

    public Viaje(String id, String routeId, String serviceId) {
        this.id = id;
        this.routeId = routeId;
        this.serviceId = serviceId;
    }

    public Viaje(String id, String routeId, String serviceId, String blockId) {
        this(id, routeId, serviceId);
        this.blockId = (blockId == null || blockId.isBlank()) ? null : blockId;
    }

    public String getId() { return id; }
    public String getRouteId() { return routeId; }
    public String getServiceId() { return serviceId; }
    public String getBlockId() { return blockId; }
    
    public void actualizarPosicion(double lat, double lon) {
        this.latitudActual = lat;
        this.longitudActual = lon;
    }
}