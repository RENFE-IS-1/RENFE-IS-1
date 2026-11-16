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

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class PreferenciasController {

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
    public List<EstacionFavorita> listarFavoritas(@RequestHeader("Authorization") String auth) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        return favoritaRepo.findByUsuarioId(u.getId());
    }

    public record FavoritaRequest(String stopId, String nombreEstacion) {}

    @PostMapping("/favoritos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public EstacionFavorita anadirFavorita(@RequestHeader("Authorization") String auth,
                                             @RequestBody FavoritaRequest req) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        if (req.stopId() == null || req.stopId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falta el identificador de la estación.");
        }
        return favoritaRepo.findByUsuarioIdAndStopId(u.getId(), req.stopId())
            .orElseGet(() -> favoritaRepo.save(new EstacionFavorita(req.stopId(), req.nombreEstacion(), u)));
    }

    @DeleteMapping("/favoritos/{stopId}")
    @Transactional
    public Map<String, String> eliminarFavorita(@RequestHeader("Authorization") String auth,
                                                @PathVariable String stopId) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        favoritaRepo.findByUsuarioIdAndStopId(u.getId(), stopId)
            .ifPresent(favoritaRepo::delete);
        return Map.of("mensaje", "Estación eliminada de favoritas.");
    }

    // --- US-015: RUTAS GUARDADAS ---

    @GetMapping("/rutas")
    public List<RutaGuardada> listarRutas(@RequestHeader("Authorization") String auth) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        return rutaRepo.findByUsuarioId(u.getId());
    }

    public record RutaRequest(String nombre, String origen, String destino, String criterio) {}

    @PostMapping("/rutas")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RutaGuardada guardarRuta(@RequestHeader("Authorization") String auth,
                                    @RequestBody RutaRequest req) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        if (req.nombre() == null || req.nombre().isBlank() || req.origen() == null || req.destino() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre, origen y destino son obligatorios.");
        }
        return rutaRepo.save(new RutaGuardada(req.nombre().trim(), req.origen().trim(), req.destino().trim(), req.criterio(), u));
    }

    @PutMapping("/rutas/{id}")
    @Transactional
    public RutaGuardada renombrarRuta(@RequestHeader("Authorization") String auth,
                                      @PathVariable Long id,
                                      @RequestBody Map<String, String> body) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        RutaGuardada ruta = rutaRepo.findByIdAndUsuarioId(id, u.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ruta guardada no encontrada."));
        String nuevoNombre = body.get("nombre");
        if (nuevoNombre == null || nuevoNombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nuevo nombre no puede estar vacío.");
        }
        ruta.setNombre(nuevoNombre.trim());
        return rutaRepo.save(ruta);
    }

    @DeleteMapping("/rutas/{id}")
    @Transactional
    public Map<String, String> eliminarRuta(@RequestHeader("Authorization") String auth,
                                            @PathVariable Long id) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        RutaGuardada ruta = rutaRepo.findByIdAndUsuarioId(id, u.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ruta guardada no encontrada."));
        rutaRepo.delete(ruta);
        return Map.of("mensaje", "Ruta guardada eliminada.");
    }
}