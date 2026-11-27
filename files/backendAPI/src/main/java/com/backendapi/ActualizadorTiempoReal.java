package com.backendapi;

import com.backendapi.servicio.ServicioIncidencias;
import loader.ClienteApiRenfe;
import repositorio.RedTransporte;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import modelo.GtfsRtJsonDto;



/**
 * Vuelve a pedir los datos en tiempo real de Renfe cada cierto tiempo (renfe.api.intervalo-ms,
 * un minuto por defecto). La primera descarga se hace justo después de arrancar, en segundo plano,
 * así el servidor no espera a la API de Renfe para empezar a responder.
 *
 * Después de cada descarga revisa los retrasos para detectar incidencias (US-017).
 */
@Component
public class ActualizadorTiempoReal {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final ClienteApiRenfe cliente;
    private final RedTransporte red;
    private final ServicioIncidencias servicioIncidencias;
    private final boolean activo;

    /**
     * Recibe el cliente de Renfe, la red donde se guardan los datos en tiempo real y el servicio que
     * detecta incidencias a partir de ellos (Spring los inyecta).
     */
    public ActualizadorTiempoReal(ClienteApiRenfe cliente, RedTransporte red,
                                  ServicioIncidencias servicioIncidencias,
                                  @Value("${renfe.api.activo:true}") boolean activo) {
        this.cliente = cliente;
        this.red = red;
        this.servicioIncidencias = servicioIncidencias;
        this.activo = activo;
    }

    /**
     * Descarga los datos en tiempo real de Renfe y, con ellos, detecta las incidencias por retraso.
     * La detección va después de la descarga porque necesita los retrasos recién actualizados.
     * Si la detección falla solo se anota el error: los datos del mapa y de las llegadas ya están
     * actualizados y no deben verse afectados.
     */
    @Scheduled(initialDelay = 0, fixedDelayString = "${renfe.api.intervalo-ms:60000}")
    public void actualizar() {
        if (!activo) return;
        cliente.actualizarTiempoReal(red);
        try {
            servicioIncidencias.detectarIncidenciasPorRetraso(LocalDateTime.now(MADRID));
        } catch (Exception e) {
            System.err.println("Fallo al detectar incidencias por retraso: " + e.getMessage());
        }
    }

    /** Devuelve las posiciones de trenes delegando en el cliente de Renfe */
    public List<GtfsRtJsonDto.VehiclePosition> getPosicionesEnVivo() {
        return cliente.getPosicionesEnVivo();
    }
}
