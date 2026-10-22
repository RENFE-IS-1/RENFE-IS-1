package com.backendapi;

import consultas.BuscadorEstaciones;
import consultas.ConsultaLlegadas;
import loader.GTFSLoader;
import modelo.Estacion;
import repositorio.RedTransporte;
import servicio.ServicioBuscadorRutas;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Crea los objetos del programa una sola vez, al arrancar el servidor, y Spring se los pasa
 * a los controladores que los necesiten.
 */
@Configuration
public class ConfiguracionRed {

    @Bean
    public RedTransporte redTransporte(@Value("${renfe.datos}") String directorio) {
        String ruta = new File(directorio).getAbsolutePath();
        File stopTimes = new File(directorio, "stop_times.txt");
        if (stopTimes.isFile() && stopTimes.length() < 10_000) {
            throw new IllegalStateException("stop_times.txt pesa " + stopTimes.length()
                    + " bytes: es un puntero de Git LFS, no los datos. Ejecuta 'git lfs pull'.");
        }

        RedTransporte red = new RedTransporte();
        new GTFSLoader().cargarDesdeDirectorio(directorio, red);
        if (red.getAllEstaciones().isEmpty()) {
            throw new IllegalStateException("No se han cargado datos desde " + ruta
                    + ". Revisa renfe.datos en application.properties.");
        }
        return red;
    }

    @Bean
    public BuscadorEstaciones buscadorEstaciones(RedTransporte red) {
        return new BuscadorEstaciones(red);
    }

    @Bean
    public ConsultaLlegadas consultaLlegadas(RedTransporte red) {
        return new ConsultaLlegadas(red);
    }

    @Bean
    public ServicioBuscadorRutas buscadorRutas(RedTransporte red) {
        ServicioBuscadorRutas buscador = new ServicioBuscadorRutas(red);
        // Una primera búsqueda al arrancar construye los índices del buscador. Así no se hace en la
        // primera petición y, después, varias peticiones a la vez solo leen esos índices.
        List<Estacion> estaciones = red.getAllEstaciones();
        buscador.buscarRutas(estaciones.get(0), estaciones.get(1), LocalDateTime.now());
        return buscador;
    }
}
