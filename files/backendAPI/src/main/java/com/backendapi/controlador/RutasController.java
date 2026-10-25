package com.backendapi.controlador;

import consultas.BuscadorEstaciones;
import consultas.ConsultaLlegadas;
import modelo.Estacion;
import modelo.Llegada;
import modelo.Ruta;
import repositorio.RedTransporte;
import servicio.ComparadoresRuta;
import servicio.ServicioBuscadorRutas;
import servicio.ServicioComparadorRutas;

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
import java.util.Comparator;
import java.util.List;

/**
 * API REST del proyecto. Se puede probar desde el navegador en /swagger-ui.html.
 */
@RestController
@RequestMapping("/api")
public class RutasController {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final RedTransporte red;
    private final BuscadorEstaciones buscadorEstaciones;
    private final ConsultaLlegadas consultaLlegadas;
    private final ServicioBuscadorRutas buscadorRutas;
    private final ServicioComparadorRutas comparadorRutas;

    public RutasController(RedTransporte red, BuscadorEstaciones buscadorEstaciones,
                           ConsultaLlegadas consultaLlegadas, ServicioBuscadorRutas buscadorRutas) {
        this.red = red;
        this.buscadorEstaciones = buscadorEstaciones;
        this.consultaLlegadas = consultaLlegadas;
        this.buscadorRutas = buscadorRutas;
        this.comparadorRutas = new ServicioComparadorRutas();
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

    /** Estaciones del recorrido más largo disponible para una línea de Cercanías. */
    @GetMapping("/lineas/{linea}/estaciones")
    public List<Estacion> estacionesDeLinea(@PathVariable("linea") String linea) {
        List<Estacion> estaciones = red.getEstacionesDeLinea(linea);
        if (estaciones.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No se encontró la línea '" + linea + "'.");
        }
        return estaciones;
    }

    /** US-002 / US-010 / US-011: rutas con trenes reales, ordenadas por hora de llegada o criterio de comparación. */
    @GetMapping("/rutas")
    public List<Ruta> rutas(
            @RequestParam(name = "origen") String origen,
            @RequestParam(name = "destino") String destino,
            @RequestParam(name = "salida", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime salida,
            @RequestParam(name = "criterio", required = false, defaultValue = "tiempo") String criterio) {
        
        LocalDateTime hora = salida != null ? salida : ahora();
        List<Ruta> encontradas = buscadorRutas.buscarRutas(estacion(origen), estacion(destino), hora);

        if (encontradas.isEmpty()) {
            return encontradas;
        }

        Comparator<Ruta> comp = switch (criterio.toLowerCase()) {
            case "precio" -> ComparadoresRuta.POR_PRECIO;
            case "transbordos" -> ComparadoresRuta.POR_TRANSBORDOS;
            default -> ComparadoresRuta.POR_TIEMPO;
        };

        return comparadorRutas.ordenarRutas(encontradas, comp);
    }

    private Estacion estacion(String texto) {
        List<Estacion> encontradas = buscadorEstaciones.buscar(texto);
        
        // 1. Sin coincidencias
        if (encontradas.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, 
                "No se encontró ninguna estación llamada '" + texto + "'. Revisa la ortografía."
            );
        }
        
        String normBuscado = BuscadorEstaciones.normalizar(texto);

        // 2. Coincidencia EXACTA (mayúsculas/tildes ignoradas)
        for (Estacion e : encontradas) {
            if (BuscadorEstaciones.normalizar(e.getNombre()).equals(normBuscado)) {
                return e;
            }
        }

        // 3. Caso especial de alias para Atocha o Chamartín
        for (Estacion e : encontradas) {
            String normNombre = BuscadorEstaciones.normalizar(e.getNombre());
            if (normNombre.contains(normBuscado) && (normBuscado.equals("atocha") || normBuscado.equals("chamartin"))) {
                return e;
            }
        }

        // 4. Si solo hay 1 coincidencia única
        if (encontradas.size() == 1) {
            return encontradas.get(0);
        }

        // 5. Si hay 2 o más coincidencias (ej: "alcala" o "san"), lanzamos el error de ambigüedad
        String listaOpciones = encontradas.stream()
                .map(Estacion::getNombre)
                .reduce((a, b) -> a + " | " + b)
                .orElse("");

        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, 
            "AMBIGUEDAD:" + texto + ":" + listaOpciones
        );
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(MADRID).withSecond(0).withNano(0);
    }
}
