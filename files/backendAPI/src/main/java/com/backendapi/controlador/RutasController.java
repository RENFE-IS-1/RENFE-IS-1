package com.backendapi.controlador;

import consultas.BuscadorEstaciones;
import consultas.ConsultaLlegadas;
import modelo.Estacion;
import modelo.Llegada;
import modelo.Ruta;
import repositorio.RedTransporte;
import servicio.ServicioBuscadorRutas;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * API REST del proyecto. Se puede probar desde el navegador en /swagger-ui.html.
 * Las fechas van en formato ISO, por ejemplo 2026-10-28T08:00:00; si no se indica, se usa la hora
 * actual de Madrid.
 */
@RestController
@RequestMapping("/api")
public class RutasController {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final RedTransporte red;
    private final BuscadorEstaciones buscadorEstaciones;
    private final ConsultaLlegadas consultaLlegadas;
    private final ServicioBuscadorRutas buscadorRutas;

    // Spring pasa aquí los objetos creados en ConfiguracionRed
    public RutasController(RedTransporte red, BuscadorEstaciones buscadorEstaciones,
                           ConsultaLlegadas consultaLlegadas, ServicioBuscadorRutas buscadorRutas) {
        this.red = red;
        this.buscadorEstaciones = buscadorEstaciones;
        this.consultaLlegadas = consultaLlegadas;
        this.buscadorRutas = buscadorRutas;
    }

    /** Todas las estaciones o, con ?nombre=, las que coinciden (sin distinguir tildes ni mayúsculas). */
    @GetMapping("/estaciones")
    public List<Estacion> estaciones(@RequestParam(name = "nombre", required = false) String nombre) {
        if (nombre == null || nombre.isBlank()) return red.getAllEstaciones();
        return buscadorEstaciones.buscar(nombre);
    }

    /** US-004: próximos trenes de una estación, opcionalmente filtrados por línea. */
    @GetMapping("/estaciones/{id}/llegadas")
    public List<Llegada> llegadas(
            @PathVariable("id") String id,
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(name = "linea", required = false) String linea,
            @RequestParam(name = "max", defaultValue = "10") int max) {
        LocalDateTime hora = desde != null ? desde : ahora();
        return consultaLlegadas.proximasLlegadas(estacion(id), hora, max, linea);
    }

    /** US-002 / US-010 / US-011: rutas con trenes reales, ordenadas por hora de llegada. */
    @GetMapping("/rutas")
    public List<Ruta> rutas(
            @RequestParam(name = "origen") String origen,
            @RequestParam(name = "destino") String destino,
            @RequestParam(name = "salida", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime salida) {
        LocalDateTime hora = salida != null ? salida : ahora();
        return buscadorRutas.buscarRutas(estacion(origen), estacion(destino), hora);
    }

    private Estacion estacion(String id) {
        Estacion e = red.getEstacion(id);
        if (e == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Estación no encontrada: " + id);
        }
        return e;
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(MADRID).withSecond(0).withNano(0);
    }
}
