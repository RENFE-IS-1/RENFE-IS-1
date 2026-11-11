package loader;

import interfaces.FuenteDatosExterna;
import modelo.GtfsRtJsonDto;
import modelo.Viaje;
import repositorio.RedTransporte;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Descarga los datos en tiempo real de Renfe (GTFS-RT en JSON): posiciones de los trenes, retrasos
 * e incidencias. Si una descarga falla, se marca como no activa y el programa sigue con los
 * horarios programados del GTFS.
 */
@Component
public class ClienteApiRenfe implements FuenteDatosExterna {

    private final RestTemplate restTemplate;
    
    @Value("${renfe.api.url.posiciones}")
    private String urlPosiciones;
    
    @Value("${renfe.api.url.horarios}")
    private String urlHorarios;

    @Value("${renfe.api.url.incidencias}")
    private String urlIncidencias;

    /**
     * Cada petición espera como mucho renfe.api.timeout-ms (5 s por defecto) para conectar y otro
     * tanto para leer. Sin límite, si la API de Renfe no responde la petición se quedaría esperando
     * para siempre.
     */
    public ClienteApiRenfe(@Value("${renfe.api.timeout-ms:5000}") long timeoutMs) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofMillis(timeoutMs));
        fabrica.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restTemplate = new RestTemplate(fabrica);
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

            Map<String, Integer> retrasos = new HashMap<>();
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
                        if (tripId != null) retrasos.put(tripId, delay);
                    }
                }
            }
            red.reemplazarRetrasos(retrasos);
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

            List<String> incidencias = new ArrayList<>();
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
                    incidencias.add(titulo + ": " + descripcion);
                }
            }
            red.reemplazarIncidencias(incidencias);
            return true;
        } catch (Exception e) {
            System.err.println("Motivo del fallo: " + e.getMessage());
            return false;
        }
    }
}
