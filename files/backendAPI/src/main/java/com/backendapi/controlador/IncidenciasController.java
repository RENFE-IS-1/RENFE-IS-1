package com.backendapi.controlador;

import com.backendapi.modelo.Incidencia;
import com.backendapi.modelo.TipoIncidenciaUsuario;
import com.backendapi.modelo.Usuario;
import com.backendapi.servicio.ServicioIncidencias;
import com.backendapi.servicio.ServicioUsuarios;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * API de incidencias declaradas por viajeros.
 *
 * - POST /api/incidencias: un VIAJERO declara una incidencia.
 * - PUT /api/incidencias/{id}/finalizar: un GESTOR cierra una incidencia declarada.
 * - GET /api/incidencias/tipos: lista cerrada de tipos que se pueden declarar.
 */
@RestController
@RequestMapping("/api/incidencias")
@CrossOrigin(origins = "*")
public class IncidenciasController {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final ServicioUsuarios servicioUsuarios;
    private final ServicioIncidencias servicioIncidencias;

    /** Recibe el servicio de usuarios (para la sesión) y el de incidencias (Spring los inyecta). */
    public IncidenciasController(ServicioUsuarios servicioUsuarios, ServicioIncidencias servicioIncidencias) {
        this.servicioUsuarios = servicioUsuarios;
        this.servicioIncidencias = servicioIncidencias;
    }

    /** Datos que envía la web al declarar una incidencia. */
    public record DeclaracionRequest(String linea, String tipo, String especificacion, String comentario) {}

    /**
     * Un viajero declara una incidencia en una línea. Lo necesitamos para que la web pueda enviar las
     * declaraciones. Devuelve 201 con la incidencia (grupo) en la que queda la declaración.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Incidencia declarar(@RequestHeader(value = "Authorization", required = false) String auth,
                               @RequestBody DeclaracionRequest req) {
        Usuario usuario = servicioUsuarios.obtenerUsuarioPorToken(auth);
        return servicioIncidencias.declararIncidencia(usuario, req.linea(), leerTipo(req.tipo()),
                req.especificacion(), req.comentario(), LocalDateTime.now(MADRID));
    }

    /** Un gestor cierra una incidencia declarada. Lo necesitamos para que las incidencias declaradas no queden abiertas para siempre. */
    @PutMapping("/{id}/finalizar")
    public Incidencia finalizar(@RequestHeader(value = "Authorization", required = false) String auth,
                                @PathVariable Long id) {
        Usuario usuario = servicioUsuarios.obtenerUsuarioPorToken(auth);
        return servicioIncidencias.finalizarIncidenciaDeclarada(usuario, id, LocalDateTime.now(MADRID));
    }

    /** Devuelve los tipos que se pueden declarar con su texto. Lo necesitamos para que la web muestre el desplegable. */
    @GetMapping("/tipos")
    public List<Map<String, Object>> tipos() {
        return Arrays.stream(TipoIncidenciaUsuario.values())
                .map(t -> Map.<String, Object>of(
                        "codigo", t.name(),
                        "etiqueta", t.getEtiqueta(),
                        "requiereEspecificacion", t.requiereEspecificacion()))
                .toList();
    }

    /**
     * Convierte el tipo recibido (p. ej. "AVERIA_TREN") en el enum; null si no viene.
     * Lo necesitamos para responder 400 con un mensaje claro si el tipo no es de la lista.
     */
    private static TipoIncidenciaUsuario leerTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) return null;
        try {
            return TipoIncidenciaUsuario.valueOf(tipo.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de incidencia no válido.");
        }
    }
}
