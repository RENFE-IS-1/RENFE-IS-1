package com.backendapi.servicio;

import com.backendapi.modelo.RolUsuario;
import com.backendapi.modelo.Usuario;
import com.backendapi.repositorio.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ServicioUsuarios {

    private final UsuarioRepository usuarioRepo;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Map<String, Long> sesionesActivas = new ConcurrentHashMap<>();

    public ServicioUsuarios(UsuarioRepository usuarioRepo) {
        this.usuarioRepo = usuarioRepo;
    }

    /**
     * Criterio 6 de US-013: Las cuentas de gestor y analista no se crean desde el registro público;
     * se precargan cuentas institucionales para demostración y administración.
     */
    @PostConstruct
    public void inicializarCuentasEspeciales() {
        if (!usuarioRepo.existsByEmailIgnoreCase("gestor@cercanias.es")) {
            usuarioRepo.save(new Usuario(
                "Gestor de Red",
                "gestor@cercanias.es",
                passwordEncoder.encode("Gestor1234!"),
                RolUsuario.GESTOR,
                true
            ));
        }
        if (!usuarioRepo.existsByEmailIgnoreCase("analista@cercanias.es")) {
            usuarioRepo.save(new Usuario(
                "Analista Movilidad",
                "analista@cercanias.es",
                passwordEncoder.encode("Analista1234!"),
                RolUsuario.ANALISTA,
                true
            ));
        }
    }

    @Transactional
    public Usuario registrarViajero(String nombre, String email, String password, boolean aceptaPrivacidad) {
        if (nombre == null || nombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre es obligatorio.");
        }
        if (email == null || !email.contains("@")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Introduce un email válido.");
        }
        if (password == null || password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 8 caracteres.");
        }
        if (!aceptaPrivacidad) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes aceptar la política de privacidad para registrarte.");
        }
        if (usuarioRepo.existsByEmailIgnoreCase(email.trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta registrada con ese email.");
        }

        String hash = passwordEncoder.encode(password);
        Usuario nuevo = new Usuario(nombre.trim(), email.trim(), hash, RolUsuario.VIAJERO, true);
        return usuarioRepo.save(nuevo);
    }

    public Map<String, Object> iniciarSesion(String email, String password) {
        ResponseStatusException errorCredenciales = new ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "Credenciales incorrectas."
        );

        if (email == null || password == null) throw errorCredenciales;

        Usuario usuario = usuarioRepo.findByEmailIgnoreCase(email.trim())
            .orElseThrow(() -> errorCredenciales);

        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw errorCredenciales;
        }

        String token = UUID.randomUUID().toString();
        sesionesActivas.put(token, usuario.getId());

        return Map.of(
            "token", token,
            "usuario", resumenUsuario(usuario)
        );
    }

    public void cerrarSesion(String token) {
        if (token != null) {
            sesionesActivas.remove(limpiarBearer(token));
        }
    }

    public Usuario obtenerUsuarioPorToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión para realizar esta acción.");
        }
        String token = limpiarBearer(authHeader);
        Long userId = sesionesActivas.get(token);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida o expirada.");
        }
        return usuarioRepo.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado."));
    }

    @Transactional
    public void eliminarCuenta(String authHeader) {
        Usuario usuario = obtenerUsuarioPorToken(authHeader);
        String token = limpiarBearer(authHeader);
        sesionesActivas.remove(token);
        usuarioRepo.delete(usuario);
    }

    public Map<String, Object> resumenUsuario(Usuario u) {
        return Map.of(
            "id", u.getId(),
            "nombre", u.getNombre(),
            "email", u.getEmail(),
            "rol", u.getRol().name(),
            "fechaRegistro", u.getFechaRegistro().toString()
        );
    }

    private String limpiarBearer(String header) {
        return header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
    }
}