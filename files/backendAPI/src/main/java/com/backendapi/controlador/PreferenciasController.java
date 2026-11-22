package com.backendapi.controlador;

import com.backendapi.modelo.EstacionFavorita;
import com.backendapi.modelo.RutaGuardada;
import com.backendapi.modelo.Usuario;
import com.backendapi.repositorio.EstacionFavoritaRepository;
import com.backendapi.repositorio.RutaGuardadaRepository;
import com.backendapi.servicio.ServicioUsuarios;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Preferencias de un viajero con sesión iniciada: estaciones favoritas (US-014) y rutas guardadas
 * (US-015). Cada operación obtiene el usuario a partir del token y solo trabaja con sus propios
 * datos: una ruta de otro usuario responde 404, igual que una que no existe.
 *
 * La cabecera Authorization es opcional en la firma para que, si falta, la respuesta sea 401 (la
 * da ServicioUsuarios) y no un 400 genérico de Spring.
 */
@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class PreferenciasController {

    // Límites de longitud: las columnas de texto de la base de datos son de 255 caracteres, y sin
    // comprobarlos un texto más largo daría un error 500 en vez de un mensaje claro
    static final int MAX_STOP_ID = 50;
    static final int MAX_TEXTO = 200;
    static final int MAX_NOMBRE_RUTA = 100;
    /** Criterios de orden que entiende /api/rutas. */
    static final java.util.Set<String> CRITERIOS = java.util.Set.of("tiempo", "precio", "transbordos");

    private final ServicioUsuarios servicioUsuarios;
    private final EstacionFavoritaRepository favoritaRepo;
    private final RutaGuardadaRepository rutaRepo;

    public PreferenciasController(ServicioUsuarios servicioUsuarios,
                                  EstacionFavoritaRepository favoritaRepo,
                                  RutaGuardadaRepository rutaRepo) {
        this.servicioUsuarios = servicioUsuarios;
        this.favoritaRepo = favoritaRepo;
        this.rutaRepo = rutaRepo;
    }

    // --- US-014: ESTACIONES FAVORITAS ---

    @GetMapping("/favoritos")
    public List<EstacionFavorita> listarFavoritas(@RequestHeader(value = "Authorization", required = false) String auth) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        return favoritaRepo.findByUsuarioId(u.getId());
    }

    public record FavoritaRequest(String stopId, String nombreEstacion) {}

    @PostMapping("/favoritos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public EstacionFavorita anadirFavorita(@RequestHeader(value = "Authorization", required = false) String auth,
                                             @RequestBody FavoritaRequest req) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        if (req.stopId() == null || req.stopId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el identificador de la estación.");
        }
        if (req.stopId().length() > MAX_STOP_ID
                || (req.nombreEstacion() != null && req.nombreEstacion().length() > MAX_TEXTO)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador o el nombre de la estación son demasiado largos.");
        }
        return favoritaRepo.findByUsuarioIdAndStopId(u.getId(), req.stopId())
            .orElseGet(() -> favoritaRepo.save(new EstacionFavorita(req.stopId(), req.nombreEstacion(), u)));
    }

    @DeleteMapping("/favoritos/{stopId}")
    @Transactional
    public Map<String, String> eliminarFavorita(@RequestHeader(value = "Authorization", required = false) String auth,
                                                @PathVariable String stopId) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        favoritaRepo.findByUsuarioIdAndStopId(u.getId(), stopId)
            .ifPresent(favoritaRepo::delete);
        return Map.of("mensaje", "Estación eliminada de favoritas.");
    }

    // --- US-015: RUTAS GUARDADAS ---

    @GetMapping("/rutas")
    public List<RutaGuardada> listarRutas(@RequestHeader(value = "Authorization", required = false) String auth) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        return rutaRepo.findByUsuarioId(u.getId());
    }

    public record RutaRequest(String nombre, String origen, String destino, String criterio) {}

    @PostMapping("/rutas")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RutaGuardada guardarRuta(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @RequestBody RutaRequest req) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        if (req.nombre() == null || req.nombre().isBlank() || req.origen() == null || req.origen().isBlank()
                || req.destino() == null || req.destino().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre, origen y destino son obligatorios.");
        }
        if (req.nombre().trim().length() > MAX_NOMBRE_RUTA || req.origen().trim().length() > MAX_TEXTO
                || req.destino().trim().length() > MAX_TEXTO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre, el origen o el destino son demasiado largos.");
        }
        // Sin criterio se guarda "tiempo" (lo hace RutaGuardada); uno desconocido no se acepta
        if (req.criterio() != null && !req.criterio().isBlank() && !CRITERIOS.contains(req.criterio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El criterio debe ser tiempo, precio o transbordos.");
        }
        return rutaRepo.save(new RutaGuardada(req.nombre().trim(), req.origen().trim(), req.destino().trim(), req.criterio(), u));
    }

    @PutMapping("/rutas/{id}")
    @Transactional
    public RutaGuardada renombrarRuta(@RequestHeader(value = "Authorization", required = false) String auth,
                                      @PathVariable Long id,
                                      @RequestBody Map<String, String> body) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        RutaGuardada ruta = rutaRepo.findByIdAndUsuarioId(id, u.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ruta guardada no encontrada."));
        String nuevoNombre = body.get("nombre");
        if (nuevoNombre == null || nuevoNombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nuevo nombre no puede estar vacío.");
        }
        if (nuevoNombre.trim().length() > MAX_NOMBRE_RUTA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es demasiado largo.");
        }
        ruta.setNombre(nuevoNombre.trim());
        return rutaRepo.save(ruta);
    }

    @DeleteMapping("/rutas/{id}")
    @Transactional
    public Map<String, String> eliminarRuta(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @PathVariable Long id) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        RutaGuardada ruta = rutaRepo.findByIdAndUsuarioId(id, u.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ruta guardada no encontrada."));
        rutaRepo.delete(ruta);
        return Map.of("mensaje", "Ruta guardada eliminada.");
    }
}