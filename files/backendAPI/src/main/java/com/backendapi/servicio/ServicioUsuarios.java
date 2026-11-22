package com.backendapi.servicio;

import com.backendapi.modelo.RolUsuario;
import com.backendapi.modelo.Usuario;
import com.backendapi.repositorio.UsuarioRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Cuentas de usuario (US-013): registro de viajeros, inicio y cierre de sesión con token y borrado
 * de cuenta.
 *
 * Las sesiones se guardan en memoria: un token aleatorio (UUID) por inicio de sesión, que caduca a
 * las {@code sesion.duracion-horas} horas. Al reiniciar el servidor se pierden y hay que volver a
 * iniciar sesión, algo aceptable para el alcance del proyecto.
 */
@Service
public class ServicioUsuarios {

    /** Duración por defecto de una sesión si no se configura otra. */
    static final Duration DURACION_SESION_POR_DEFECTO = Duration.ofHours(8);
    static final int MAX_NOMBRE = 100;
    static final int MAX_EMAIL = 254;
    /** BCrypt solo usa los primeros 72 bytes de la contraseña: lo que pase de ahí se ignoraría. */
    static final int MAX_PASSWORD_BYTES = 72;
    static final int MIN_PASSWORD = 8;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Token de una sesión: a qué usuario pertenece y hasta cuándo vale. */
    private record Sesion(Long usuarioId, Instant expira) {}

    private final UsuarioRepository usuarioRepo;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Map<String, Sesion> sesionesActivas = new ConcurrentHashMap<>();
    private final Duration duracionSesion;
    private final Clock reloj;
    /**
     * Hash de una contraseña cualquiera. Cuando el email no existe se compara igualmente contra él,
     * para que el inicio de sesión tarde lo mismo exista o no la cuenta y no se pueda averiguar
     * qué emails están registrados midiendo el tiempo de respuesta.
     */
    private final String hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());

    /** Constructor que usa Spring; la duración de la sesión se lee de {@code sesion.duracion-horas}. */
    @Autowired
    public ServicioUsuarios(UsuarioRepository usuarioRepo,
                            @Value("${sesion.duracion-horas:8}") long horasSesion) {
        this(usuarioRepo, Duration.ofHours(horasSesion), Clock.systemUTC());
    }

    /** Para pruebas: duración de sesión por defecto. */
    public ServicioUsuarios(UsuarioRepository usuarioRepo) {
        this(usuarioRepo, DURACION_SESION_POR_DEFECTO, Clock.systemUTC());
    }

    /** Para pruebas que necesitan controlar la hora (caducidad de sesiones). */
    ServicioUsuarios(UsuarioRepository usuarioRepo, Duration duracionSesion, Clock reloj) {
        this.usuarioRepo = usuarioRepo;
        this.duracionSesion = duracionSesion;
        this.reloj = reloj;
    }

    /**
     * Criterio 6 de US-013: las cuentas de gestor y analista no se crean desde el registro público,
     * sino al arrancar la aplicación si todavía no existen.
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
        if (nombre.trim().length() > MAX_NOMBRE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El nombre no puede tener más de " + MAX_NOMBRE + " caracteres.");
        }
        if (email == null || email.trim().length() > MAX_EMAIL || !EMAIL.matcher(email.trim()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Introduce un email válido.");
        }
        if (password == null || password.length() < MIN_PASSWORD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña debe tener al menos " + MIN_PASSWORD + " caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña es demasiado larga.");
        }
        if (!aceptaPrivacidad) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debes aceptar la política de privacidad para registrarte.");
        }
        if (usuarioRepo.existsByEmailIgnoreCase(email.trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta registrada con ese email.");
        }

        String hash = passwordEncoder.encode(password);
        Usuario nuevo = new Usuario(nombre.trim(), email.trim(), hash, RolUsuario.VIAJERO, true);
        try {
            return usuarioRepo.save(nuevo);
        } catch (DataIntegrityViolationException e) {
            // Dos registros a la vez con el mismo email: los dos pasan la comprobación de arriba y
            // la base de datos rechaza el segundo por la restricción UNIQUE. Se responde 409 igual.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta registrada con ese email.");
        }
    }

    public Map<String, Object> iniciarSesion(String email, String password) {
        ResponseStatusException errorCredenciales = new ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "Credenciales incorrectas."
        );

        if (email == null || password == null) throw errorCredenciales;

        Usuario usuario = usuarioRepo.findByEmailIgnoreCase(email.trim()).orElse(null);
        // Mismo trabajo (una comparación BCrypt) exista o no la cuenta, y el mismo mensaje
        if (usuario == null) {
            passwordEncoder.matches(password, hashFicticio);
            throw errorCredenciales;
        }
        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw errorCredenciales;
        }

        purgarSesionesCaducadas();
        String token = UUID.randomUUID().toString();
        sesionesActivas.put(token, new Sesion(usuario.getId(), reloj.instant().plus(duracionSesion)));

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
        Sesion sesion = sesionesActivas.get(token);
        if (sesion == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida o expirada.");
        }
        if (!reloj.instant().isBefore(sesion.expira())) {
            sesionesActivas.remove(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida o expirada.");
        }
        return usuarioRepo.findById(sesion.usuarioId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado."));
    }

    @Transactional
    public void eliminarCuenta(String authHeader) {
        Usuario usuario = obtenerUsuarioPorToken(authHeader);
        // Se cierran todas las sesiones de ese usuario, no solo la de esta petición (puede haber
        // iniciado sesión en varios navegadores)
        sesionesActivas.values().removeIf(s -> s.usuarioId().equals(usuario.getId()));
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

    /** Número de sesiones abiertas (para pruebas). */
    int numeroSesionesActivas() {
        return sesionesActivas.size();
    }

    /** Quita las sesiones caducadas, para que el mapa no crezca sin límite. */
    private void purgarSesionesCaducadas() {
        Instant ahora = reloj.instant();
        sesionesActivas.values().removeIf(s -> !ahora.isBefore(s.expira()));
    }

    private String limpiarBearer(String header) {
        return header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
    }
}
