package com.backendapi.servicio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.backendapi.modelo.RolUsuario;
import com.backendapi.modelo.Usuario;
import com.backendapi.repositorio.UsuarioRepository;

class ServicioUsuariosTest {

    private UsuarioRepository usuarioRepo;
    private ServicioUsuarios servicio;

    @BeforeEach
    void preparar() {
        usuarioRepo = mock(UsuarioRepository.class);
        servicio = new ServicioUsuarios(usuarioRepo);
    }

    @Test
    void registrarViajero_datosValidos_creaCuentaConRolViajeroYPasswordEncriptada() {
        when(usuarioRepo.existsByEmailIgnoreCase("ana@cercanias.es")).thenReturn(false);
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 10L);
            return u;
        });

        Usuario creado = servicio.registrarViajero("  Ana García  ", "ana@cercanias.es", "ClaveSegura123", true);

        assertEquals("Ana García", creado.getNombre());
        assertEquals("ana@cercanias.es", creado.getEmail());
        assertEquals(RolUsuario.VIAJERO, creado.getRol());
        assertNotEquals("ClaveSegura123", creado.getPasswordHash());
        assertTrue(creado.getPasswordHash().startsWith("$2"));
    }

    @Test
    void registrarViajero_rechazaNombreVacioEmailInvalidoPasswordCortaYPrivacidadNoAceptada() {
        ResponseStatusException errNombre = assertThrows(ResponseStatusException.class,
                () -> servicio.registrarViajero("   ", "ana@cercanias.es", "ClaveSegura123", true));
        assertEquals(HttpStatus.BAD_REQUEST, errNombre.getStatusCode());

        ResponseStatusException errEmail = assertThrows(ResponseStatusException.class,
                () -> servicio.registrarViajero("Ana", "correo-sin-arroba", "ClaveSegura123", true));
        assertEquals(HttpStatus.BAD_REQUEST, errEmail.getStatusCode());

        ResponseStatusException errPass = assertThrows(ResponseStatusException.class,
                () -> servicio.registrarViajero("Ana", "ana@cercanias.es", "1234567", true));
        assertEquals(HttpStatus.BAD_REQUEST, errPass.getStatusCode());

        ResponseStatusException errPriv = assertThrows(ResponseStatusException.class,
                () -> servicio.registrarViajero("Ana", "ana@cercanias.es", "ClaveSegura123", false));
        assertEquals(HttpStatus.BAD_REQUEST, errPriv.getStatusCode());

        verify(usuarioRepo, never()).save(any());
    }

    @Test
    void registrarViajero_emailDuplicado_devuelveConflict() {
        when(usuarioRepo.existsByEmailIgnoreCase("repe@cercanias.es")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.registrarViajero("Ana", "repe@cercanias.es", "ClaveSegura123", true));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(usuarioRepo, never()).save(any());
    }

    @Test
    void iniciarSesionYValidarTokenConBearer_funcionaYSeInvalidaAlCerrarSesion() {
        when(usuarioRepo.existsByEmailIgnoreCase("luis@cercanias.es")).thenReturn(false);
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 25L);
            return u;
        });

        Usuario guardado = servicio.registrarViajero("Luis", "luis@cercanias.es", "MiPassword99", true);
        when(usuarioRepo.findByEmailIgnoreCase("luis@cercanias.es")).thenReturn(Optional.of(guardado));
        when(usuarioRepo.findById(25L)).thenReturn(Optional.of(guardado));

        Map<String, Object> sesion = servicio.iniciarSesion("luis@cercanias.es", "MiPassword99");
        String token = (String) sesion.get("token");
        assertNotNull(token);

        Usuario autenticado = servicio.obtenerUsuarioPorToken("Bearer " + token);
        assertEquals("Luis", autenticado.getNombre());

        servicio.cerrarSesion("Bearer " + token);

        ResponseStatusException trasLogout = assertThrows(ResponseStatusException.class,
                () -> servicio.obtenerUsuarioPorToken("Bearer " + token));
        assertEquals(HttpStatus.UNAUTHORIZED, trasLogout.getStatusCode());
    }

    @Test
    void iniciarSesion_credencialesIncorrectas_devuelveUnauthorized() {
        when(usuarioRepo.findByEmailIgnoreCase("nadie@cercanias.es")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.iniciarSesion("nadie@cercanias.es", "cualquiera"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void eliminarCuenta_borraUsuarioEInvalidaSesion() {
        Usuario u = new Usuario("Mario", "mario@cercanias.es", "hash", RolUsuario.VIAJERO, true);
        ReflectionTestUtils.setField(u, "id", 42L);

        when(usuarioRepo.existsByEmailIgnoreCase("mario@cercanias.es")).thenReturn(false);
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario creado = inv.getArgument(0);
            ReflectionTestUtils.setField(creado, "id", 42L);
            return creado;
        });

        Usuario registrado = servicio.registrarViajero("Mario", "mario@cercanias.es", "Clave1234", true);
        when(usuarioRepo.findByEmailIgnoreCase("mario@cercanias.es")).thenReturn(Optional.of(registrado));
        when(usuarioRepo.findById(42L)).thenReturn(Optional.of(registrado));

        String token = (String) servicio.iniciarSesion("mario@cercanias.es", "Clave1234").get("token");

        servicio.eliminarCuenta("Bearer " + token);

        verify(usuarioRepo).delete(registrado);
        assertThrows(ResponseStatusException.class, () -> servicio.obtenerUsuarioPorToken(token));
    }

    @Test
    void inicializarCuentasEspeciales_creaGestorYAnalistaSiNoExisten() {
        when(usuarioRepo.existsByEmailIgnoreCase("gestor@cercanias.es")).thenReturn(false);
        when(usuarioRepo.existsByEmailIgnoreCase("analista@cercanias.es")).thenReturn(false);

        servicio.inicializarCuentasEspeciales();

        verify(usuarioRepo, times(2)).save(any(Usuario.class));
    }

    // ---- Casos límite añadidos en la revisión del código ----

    /** Reloj que se puede adelantar a mano, para probar la caducidad de las sesiones. */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-11-22T10:00:00Z");
        void avanzar(Duration d) { ahora = ahora.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    /** Registra un viajero con id fijo y deja preparado el repositorio para que pueda iniciar sesión. */
    private Usuario registrarConId(String email, String password, long id) {
        when(usuarioRepo.existsByEmailIgnoreCase(email)).thenReturn(false);
        when(usuarioRepo.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", id);
            return u;
        });
        Usuario u = servicio.registrarViajero("Prueba", email, password, true);
        when(usuarioRepo.findByEmailIgnoreCase(email)).thenReturn(Optional.of(u));
        when(usuarioRepo.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void sesion_caducaPasadaSuDuracion() {
        RelojManual reloj = new RelojManual();
        servicio = new ServicioUsuarios(usuarioRepo, Duration.ofHours(8), reloj);
        registrarConId("caduca@cercanias.es", "ClaveSegura123", 7L);
        String token = (String) servicio.iniciarSesion("caduca@cercanias.es", "ClaveSegura123").get("token");

        reloj.avanzar(Duration.ofHours(7).plusMinutes(59));
        assertEquals(Long.valueOf(7), servicio.obtenerUsuarioPorToken("Bearer " + token).getId());

        reloj.avanzar(Duration.ofMinutes(1));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.obtenerUsuarioPorToken("Bearer " + token));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals(0, servicio.numeroSesionesActivas());
    }

    @Test
    void eliminarCuenta_cierraTodasLasSesionesDelUsuario() {
        registrarConId("varias@cercanias.es", "ClaveSegura123", 8L);
        String movil = (String) servicio.iniciarSesion("varias@cercanias.es", "ClaveSegura123").get("token");
        String portatil = (String) servicio.iniciarSesion("varias@cercanias.es", "ClaveSegura123").get("token");

        servicio.eliminarCuenta("Bearer " + movil);

        assertEquals(0, servicio.numeroSesionesActivas());
        assertThrows(ResponseStatusException.class, () -> servicio.obtenerUsuarioPorToken("Bearer " + portatil));
    }

    @Test
    void registrarViajero_rechazaEmailsMalFormados() {
        for (String email : new String[] {"a@b", "@cercanias.es", "ana@", "ana @cercanias.es", "ana@@cercanias.es"}) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> servicio.registrarViajero("Ana", email, "ClaveSegura123", true), email);
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode(), email);
        }
        verify(usuarioRepo, never()).save(any());
    }

    @Test
    void registrarViajero_rechazaNombreYPasswordDemasiadoLargos() {
        ResponseStatusException nombre = assertThrows(ResponseStatusException.class,
                () -> servicio.registrarViajero("x".repeat(101), "ana@cercanias.es", "ClaveSegura123", true));
        assertEquals(HttpStatus.BAD_REQUEST, nombre.getStatusCode());

        // BCrypt ignoraría todo lo que pase de 72 bytes: se rechaza en vez de recortarla en silencio
        ResponseStatusException pass = assertThrows(ResponseStatusException.class,
                () -> servicio.registrarViajero("Ana", "ana@cercanias.es", "ñ".repeat(37), true));
        assertEquals(HttpStatus.BAD_REQUEST, pass.getStatusCode());
        verify(usuarioRepo, never()).save(any());
    }

    @Test
    void registrarViajero_dosRegistrosALaVez_elSegundoRecibeConflict() {
        // La comprobación previa no ve la otra cuenta, pero la base de datos la rechaza por UNIQUE
        when(usuarioRepo.existsByEmailIgnoreCase("carrera@cercanias.es")).thenReturn(false);
        when(usuarioRepo.save(any(Usuario.class))).thenThrow(new DataIntegrityViolationException("unique"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.registrarViajero("Ana", "carrera@cercanias.es", "ClaveSegura123", true));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void iniciarSesion_mismoMensajeExistaONoLaCuenta() {
        registrarConId("existe@cercanias.es", "ClaveSegura123", 9L);
        when(usuarioRepo.findByEmailIgnoreCase("noexiste@cercanias.es")).thenReturn(Optional.empty());

        ResponseStatusException malaPassword = assertThrows(ResponseStatusException.class,
                () -> servicio.iniciarSesion("existe@cercanias.es", "otraClave999"));
        ResponseStatusException sinCuenta = assertThrows(ResponseStatusException.class,
                () -> servicio.iniciarSesion("noexiste@cercanias.es", "otraClave999"));

        assertEquals(HttpStatus.UNAUTHORIZED, malaPassword.getStatusCode());
        assertEquals(malaPassword.getReason(), sinCuenta.getReason());
    }

    @Test
    void obtenerUsuarioPorToken_sinCabeceraOTokenInventado_devuelveUnauthorized() {
        for (String cabecera : new String[] {null, "", "   ", "Bearer ", "Bearer inventado"}) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> servicio.obtenerUsuarioPorToken(cabecera));
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        }
    }
}