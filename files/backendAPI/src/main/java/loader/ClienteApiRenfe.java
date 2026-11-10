package loader;

import interfaces.FuenteDatosExterna;
import modelo.GtfsRtJsonDto;
import modelo.Viaje;
import repositorio.RedTransporte;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ClienteApiRenfe implements FuenteDatosExterna {

    private final RestTemplate restTemplate;
    
    @Value("${renfe.api.url.posiciones}")
    private String urlPosiciones;
    
    @Value("${renfe.api.url.horarios}")
    private String urlHorarios;

    @Value("${renfe.api.url.incidencias}")
    private String urlIncidencias;

    public ClienteApiRenfe() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public void conectar() {
    }

    @Override
    public void cargarDatos(RedTransporte red) {
        actualizarTiempoReal(red);
    }

    @Override
    public void actualizarTiempoReal(RedTransporte red) {
        red.limpiarIncidencias();

        boolean exitoPosiciones = actualizarPosiciones(red);
        red.setPosicionesTiempoRealActivas(exitoPosiciones);

        boolean exitoHorarios = actualizarHorarios(red);
        red.setHorariosTiempoRealActivos(exitoHorarios);

        boolean exitoIncidencias = actualizarIncidencias(red);
        red.setIncidenciasTiempoRealActivas(exitoIncidencias);

        if (!exitoPosiciones) System.err.println("Fallo T-API: Posiciones. Fallback GTFS activado para ubicaciones.");
        if (!exitoHorarios) System.err.println("Fallo T-API: Horarios. Fallback GTFS activado para cálculos de tiempo.");
        if (!exitoIncidencias) System.err.println("Fallo T-API: Incidencias no disponibles.");
    }

    private boolean actualizarPosiciones(RedTransporte red) {
        try {
            GtfsRtJsonDto datos = restTemplate.getForObject(urlPosiciones, GtfsRtJsonDto.class);
            if (datos == null || datos.getEntity() == null) return false;

            for (GtfsRtJsonDto.Entity e : datos.getEntity()) {
                if (e.getVehicle() != null && e.getVehicle().getTrip() != null && e.getVehicle().getPosition() != null) {
                    String tripId = e.getVehicle().getTrip().getTripId();
                    Viaje viaje = red.getViaje(tripId);
                    if (viaje != null) {
                        viaje.actualizarPosicion(
                            e.getVehicle().getPosition().getLatitude(), 
                            e.getVehicle().getPosition().getLongitude()
                        );
                    }
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("Motivo del fallo: " + e.getMessage());
            return false;
        }
    }

    private boolean actualizarHorarios(RedTransporte red) {
        try {
            GtfsRtJsonDto datos = restTemplate.getForObject(urlHorarios, GtfsRtJsonDto.class);
            if (datos == null || datos.getEntity() == null) return false;

            for (GtfsRtJsonDto.Entity e : datos.getEntity()) {
                if (e.getTripUpdate() != null && e.getTripUpdate().getTrip() != null) {
                    String tripId = e.getTripUpdate().getTrip().getTripId();
                    if (e.getTripUpdate().getStopTimeUpdate() != null && !e.getTripUpdate().getStopTimeUpdate().isEmpty()) {
                        GtfsRtJsonDto.StopTimeUpdate stopUpdate = e.getTripUpdate().getStopTimeUpdate().get(0);
                        int delay = 0;
                        if (stopUpdate.getArrival() != null) {
                            delay = stopUpdate.getArrival().getDelay();
                        } else if (stopUpdate.getDeparture() != null) {
                            delay = stopUpdate.getDeparture().getDelay();
                        }
                        red.registrarRetrasoViaje(tripId, delay);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("Motivo del fallo: " + e.getMessage());
            return false;
        }
    }

    private boolean actualizarIncidencias(RedTransporte red) {
        try {
            GtfsRtJsonDto datos = restTemplate.getForObject(urlIncidencias, GtfsRtJsonDto.class);
            if (datos == null || datos.getEntity() == null) return false;

            for (GtfsRtJsonDto.Entity e : datos.getEntity()) {
                if (e.getAlert() != null) {
                    String titulo = "Incidencia";
                    if (e.getAlert().getHeaderText() != null && e.getAlert().getHeaderText().getTranslation() != null && !e.getAlert().getHeaderText().getTranslation().isEmpty()) {
                        titulo = e.getAlert().getHeaderText().getTranslation().get(0).getText();
                    }
                    String descripcion = "";
                    if (e.getAlert().getDescriptionText() != null && e.getAlert().getDescriptionText().getTranslation() != null && !e.getAlert().getDescriptionText().getTranslation().isEmpty()) {
                        descripcion = e.getAlert().getDescriptionText().getTranslation().get(0).getText();
                    }
                    red.addIncidencia(titulo + ": " + descripcion);
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("Motivo del fallo: " + e.getMessage());
            return false;
        }
    }
}