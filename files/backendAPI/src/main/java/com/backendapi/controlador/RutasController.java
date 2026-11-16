package com.backendapi.controlador;

import com.backendapi.ActualizadorTiempoReal;
import consultas.BuscadorEstaciones;
import consultas.ConsultaLlegadas;
import modelo.Estacion;
import modelo.EstadoTren;
import modelo.Llegada;
import modelo.LlegadaDTO;
import modelo.Ruta;
import repositorio.RedTransporte;
import servicio.ComparadoresRuta;
import servicio.ServicioBuscadorRutas;
import servicio.ServicioComparadorRutas;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import modelo.GtfsRtJsonDto;

/**
 * API REST del proyecto. Se puede probar desde el navegador en /swagger-ui.html.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RutasController {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final RedTransporte red;
    private final BuscadorEstaciones buscadorEstaciones;
    private final ConsultaLlegadas consultaLlegadas;
    private final ServicioBuscadorRutas buscadorRutas;
    private final ServicioComparadorRutas comparadorRutas;
    private final ActualizadorTiempoReal actualizadorTiempoReal;

    // 1. Constructor de 4 parámetros (para que los tests de integración sigan funcionando)
    public RutasController(RedTransporte red, BuscadorEstaciones buscadorEstaciones,
                           ConsultaLlegadas consultaLlegadas, ServicioBuscadorRutas buscadorRutas) {
        this(red, buscadorEstaciones, consultaLlegadas, buscadorRutas, null);
    }

    // 2. Constructor principal de 5 parámetros (usado por Spring Boot)
    @Autowired
    public RutasController(RedTransporte red, BuscadorEstaciones buscadorEstaciones,
                           ConsultaLlegadas consultaLlegadas, ServicioBuscadorRutas buscadorRutas,
                           ActualizadorTiempoReal actualizadorTiempoReal) {
        this.red = red;
        this.buscadorEstaciones = buscadorEstaciones;
        this.consultaLlegadas = consultaLlegadas;
        this.buscadorRutas = buscadorRutas;
        this.comparadorRutas = new ServicioComparadorRutas();
        this.actualizadorTiempoReal = actualizadorTiempoReal;
    }

    /** US-024: Devuelve las posiciones en tiempo real de los trenes para el mapa. */
    @GetMapping("/trenes/posiciones")
    public List<GtfsRtJsonDto.VehiclePosition> obtenerPosicionesTrenes() {
        if (actualizadorTiempoReal == null) {
            return java.util.Collections.emptyList();
        }
        return actualizadorTiempoReal.getPosicionesEnVivo();
    }
    
    /** US-024: Sitúa cada tren sobre el tramo de vía entre las dos estaciones más próximas a su GPS. */
    @GetMapping("/trenes/mapa")
    public java.util.Map<String, Object> obtenerTrenesParaMapa() {
        List<GtfsRtJsonDto.VehiclePosition> posiciones = (actualizadorTiempoReal != null)
                ? actualizadorTiempoReal.getPosicionesEnVivo()
                : java.util.Collections.emptyList();

        List<Estacion> todasEstaciones = red.getAllEstaciones();
        java.util.List<java.util.Map<String, Object>> trenes = new java.util.ArrayList<>();

        for (GtfsRtJsonDto.VehiclePosition vp : posiciones) {
            if (vp == null || vp.getPosition() == null) continue;

            double lat = vp.getPosition().getLatitude();
            double lon = vp.getPosition().getLongitude();
            if (lat == 0.0 && lon == 0.0) continue;

            String tripId = (vp.getTrip() != null && vp.getTrip().getTripId() != null) ? vp.getTrip().getTripId() : "";
            String linea = extraerLineaDeRouteId(tripId);

            // Filtramos estaciones de esa línea si la conocemos; si no, usamos todas
            List<Estacion> candidatas = !linea.isBlank() ? red.getEstacionesDeLinea(linea) : java.util.Collections.emptyList();
            if (candidatas.size() < 2) candidatas = todasEstaciones;

            Estacion[] dosCercanas = buscarDosEstacionesMasCercanas(candidatas, lat, lon);
            Estacion estA = dosCercanas[0];
            Estacion estB = dosCercanas[1];
            if (estA == null) continue;

            double progreso = 0.0;
            String hastaId = estA.getId();

            if (estB != null) {
                double dAx = estB.getLatitud() - estA.getLatitud();
                double dAy = estB.getLongitud() - estA.getLongitud();
                double len2 = dAx * dAx + dAy * dAy;
                if (len2 > 1e-10) {
                    double t = ((lat - estA.getLatitud()) * dAx + (lon - estA.getLongitud()) * dAy) / len2;
                    progreso = Math.max(0.0, Math.min(1.0, t));
                    hastaId = estB.getId();
                }
            }

            java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("tripId", tripId);
            item.put("linea", linea);
            item.put("destino", "");
            item.put("retrasoSegundos", 0);
            item.put("desde", estA.getId());
            item.put("hasta", hastaId);
            item.put("progreso", Math.round(progreso * 100.0) / 100.0);
            trenes.add(item);
        }

        java.time.Instant actualizado = !trenes.isEmpty() ? java.time.Instant.now() : null;

        java.util.Map<String, Object> respuesta = new java.util.LinkedHashMap<>();
        respuesta.put("disponible", actualizadorTiempoReal != null);
        respuesta.put("actualizado", actualizado != null ? actualizado.toString() : null);
        respuesta.put("obsoleto", false);
        respuesta.put("trenes", trenes);
        return respuesta;
    }

    private Estacion[] buscarDosEstacionesMasCercanas(List<Estacion> estaciones, double lat, double lon) {
        Estacion primera = null, segunda = null;
        double dist1 = Double.MAX_VALUE, dist2 = Double.MAX_VALUE;
        for (Estacion e : estaciones) {
            double dLat = e.getLatitud() - lat;
            double dLon = e.getLongitud() - lon;
            double d2 = dLat * dLat + dLon * dLon;
            if (d2 < dist1) {
                dist2 = dist1; segunda = primera;
                dist1 = d2; primera = e;
            } else if (d2 < dist2) {
                dist2 = d2; segunda = e;
            }
        }
        return new Estacion[]{ primera, segunda };
    }

    private String extraerLineaDeRouteId(String routeId) {
        if (routeId == null || routeId.isBlank()) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(C\\d+[a-z]?)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(routeId);
        return m.find() ? m.group(1).toUpperCase() : routeId;
    }

    /** Todas las estaciones o, con ?nombre=, las que coinciden (sin distinguir tildes ni mayúsculas). */
    @GetMapping("/estaciones")
    public List<Estacion> estaciones(@RequestParam(name = "nombre", required = false) String nombre) {
        if (nombre == null || nombre.isBlank()) return red.getAllEstaciones();
        return buscadorEstaciones.buscar(nombre);
    }

    /** US-004: próximos trenes de una estación adaptados al DTO de tiempo real. */
    @GetMapping("/estaciones/{id}/llegadas")
    public List<LlegadaDTO> llegadas(
            @PathVariable("id") String id,
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(name = "linea", required = false) String linea,
            @RequestParam(name = "max", defaultValue = "10") int max) {
        
        LocalDateTime hora = desde != null ? desde : ahora();
        List<Llegada> listaLlegadas = consultaLlegadas.proximasLlegadas(estacion(id), hora, max, linea);

        return listaLlegadas.stream().map(llegada -> {
            int retraso = llegada.retrasoMinutos();
            boolean esRT = llegada.tiempoReal();

            EstadoTren estado;
            if (retraso > 0) {
                estado = EstadoTren.CON_RETRASO;
            } else {
                estado = EstadoTren.EN_HORA;
            }

            LocalTime horaPlanificada = llegada.hora().toLocalTime();
            LocalTime horaEstimada = horaPlanificada.plusMinutes(Math.max(0, retraso));

            String nombreLinea = (llegada.linea() != null) ? llegada.linea().getShortName() : "";
            String nombreDestino = (llegada.destino() != null) ? llegada.destino().getNombre() : "";

            return new LlegadaDTO(
                nombreLinea,
                nombreDestino,
                horaPlanificada,
                horaEstimada,
                retraso,
                estado,
                esRT,
                esRT ? llegada.ultimaActualizacion() : null
            );
        }).toList();
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

    /** Un ramal de una línea (por ejemplo C4a) con sus estaciones en orden. */
    public record Ramal(String nombre, List<Estacion> estaciones) {}

    /**
     * Recorrido completo de una línea para dibujarlo en la web. Las líneas con bifurcación (C4 y C8)
     * devuelven un ramal por cada rama (C4a y C4b, C8a y C8b); las demás, un solo ramal.
     */
    @GetMapping("/lineas/{linea}/ramales")
    public List<Ramal> ramalesDeLinea(@PathVariable("linea") String linea) {
        List<Ramal> ramales = new java.util.ArrayList<>();
        for (String nombre : red.getRamales(linea)) {
            List<Estacion> estaciones = red.getRecorridoCompleto(nombre);
            if (!estaciones.isEmpty()) ramales.add(new Ramal(nombre, estaciones));
        }
        if (ramales.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la línea '" + linea + "'.");
        }
        return ramales;
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
