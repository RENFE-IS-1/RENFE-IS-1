package com.backendapi;

import loader.ClienteApiRenfe;
import repositorio.RedTransporte;
import com.backendapi.modelo.gtfsrt.GtfsRtFeed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import modelo.GtfsRtJsonDto;



/**
 * Vuelve a pedir los datos en tiempo real de Renfe cada cierto tiempo (renfe.api.intervalo-ms,
 * un minuto por defecto). La primera descarga se hace justo después de arrancar, en segundo plano,
 * así el servidor no espera a la API de Renfe para empezar a responder.
 */
@Component
public class ActualizadorTiempoReal {

    private final ClienteApiRenfe cliente;
    private final RedTransporte red;
    private final boolean activo;

    public ActualizadorTiempoReal(ClienteApiRenfe cliente, RedTransporte red,
                                  @Value("${renfe.api.activo:true}") boolean activo) {
        this.cliente = cliente;
        this.red = red;
        this.activo = activo;
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${renfe.api.intervalo-ms:60000}")
    public void actualizar() {
        if (activo) cliente.actualizarTiempoReal(red);
    }

    /** Devuelve las posiciones de trenes delegando en el cliente de Renfe */
    public List<GtfsRtJsonDto.VehiclePosition> getPosicionesEnVivo() {
        return cliente.getPosicionesEnVivo();
    }
}