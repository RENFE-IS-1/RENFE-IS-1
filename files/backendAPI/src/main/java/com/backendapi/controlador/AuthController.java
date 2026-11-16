package com.backendapi.controlador;

import com.backendapi.modelo.Usuario;
import com.backendapi.servicio.ServicioUsuarios;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AuthController {

    private final ServicioUsuarios servicioUsuarios;

    public AuthController(ServicioUsuarios servicioUsuarios) {
        this.servicioUsuarios = servicioUsuarios;
    }

    public record RegistroRequest(String nombre, String email, String password, boolean aceptaPrivacidad) {}
    public record LoginRequest(String email, String password) {}

    /** US-013: Registro público (siempre rol VIAJERO). */
    @PostMapping("/auth/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> registro(@RequestBody RegistroRequest req) {
        Usuario creado = servicioUsuarios.registrarViajero(
            req.nombre(), req.email(), req.password(), req.aceptaPrivacidad()
        );
        return Map.of(
            "mensaje", "Cuenta creada correctamente.",
            "usuario", servicioUsuarios.resumenUsuario(creado)
        );
    }

    /** US-013: Inicio de sesión. */
    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        return servicioUsuarios.iniciarSesion(req.email(), req.password());
    }

    /** US-013: Cierre de sesión invalidando el token. */
    @PostMapping("/auth/logout")
    public Map<String, String> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        servicioUsuarios.cerrarSesion(auth);
        return Map.of("mensaje", "Sesión cerrada correctamente.");
    }

    /** Devuelve los datos del usuario autenticado. */
    @GetMapping("/usuarios/me")
    public Map<String, Object> perfil(@RequestHeader(value = "Authorization", required = false) String auth) {
        Usuario u = servicioUsuarios.obtenerUsuarioPorToken(auth);
        return servicioUsuarios.resumenUsuario(u);
    }

    /** US-013 (Criterio 8): Elimina la cuenta del usuario junto con todos sus datos. */
    @DeleteMapping("/usuarios/me")
    public Map<String, String> eliminarCuenta(@RequestHeader(value = "Authorization", required = false) String auth) {
        servicioUsuarios.eliminarCuenta(auth);
        return Map.of("mensaje", "Cuenta y datos asociados eliminados permanentemente.");
    }
}