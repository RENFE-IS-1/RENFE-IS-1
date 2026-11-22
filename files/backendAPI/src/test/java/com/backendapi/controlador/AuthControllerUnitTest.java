package com.backendapi.controlador;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.backendapi.controlador.AuthController.LoginRequest;
import com.backendapi.controlador.AuthController.RegistroRequest;
import com.backendapi.modelo.RolUsuario;
import com.backendapi.modelo.Usuario;
import com.backendapi.servicio.ServicioUsuarios;

class AuthControllerUnitTest {

    private ServicioUsuarios servicioUsuarios;
    private AuthController controller;

    @BeforeEach
    void preparar() {
        servicioUsuarios = mock(ServicioUsuarios.class);
        controller = new AuthController(servicioUsuarios);
    }

    @Test
    void registro_devuelveMensajeYResumenDeUsuarioCreado() {
        Usuario creado = new Usuario("Sofía", "sofia@cercanias.es", "hash", RolUsuario.VIAJERO, true);
        ReflectionTestUtils.setField(creado, "id", 15L);

        when(servicioUsuarios.registrarViajero("Sofía", "sofia@cercanias.es", "ClaveSegura123", true))
                .thenReturn(creado);
        when(servicioUsuarios.resumenUsuario(creado)).thenReturn(Map.of(
                "id", 15L,
                "nombre", "Sofía",
                "email", "sofia@cercanias.es",
                "rol", "VIAJERO"
        ));

        Map<String, Object> respuesta = controller.registro(
                new RegistroRequest("Sofía", "sofia@cercanias.es", "ClaveSegura123", true));

        assertEquals("Cuenta creada correctamente.", respuesta.get("mensaje"));
        assertNotNull(respuesta.get("usuario"));
        verify(servicioUsuarios).registrarViajero("Sofía", "sofia@cercanias.es", "ClaveSegura123", true);
    }

    @Test
    void login_delegaCredencialesYDevuelveTokenYUsuario() {
        Map<String, Object> esperado = Map.of("token", "tok-123", "usuario", Map.of("email", "sofia@cercanias.es"));
        when(servicioUsuarios.iniciarSesion("sofia@cercanias.es", "ClaveSegura123")).thenReturn(esperado);

        Map<String, Object> respuesta = controller.login(
                new LoginRequest("sofia@cercanias.es", "ClaveSegura123"));

        assertEquals("tok-123", respuesta.get("token"));
    }

    @Test
    void logout_invalidaTokenYConfirmaCierreDeSesion() {
        Map<String, String> respuesta = controller.logout("Bearer tok-123");

        verify(servicioUsuarios).cerrarSesion("Bearer tok-123");
        assertEquals("Sesión cerrada correctamente.", respuesta.get("mensaje"));
    }

    @Test
    void perfil_devuelveResumenDelUsuarioAutenticado() {
        Usuario u = new Usuario("Sofía", "sofia@cercanias.es", "hash", RolUsuario.VIAJERO, true);
        when(servicioUsuarios.obtenerUsuarioPorToken("Bearer tok-123")).thenReturn(u);
        when(servicioUsuarios.resumenUsuario(u)).thenReturn(Map.of("email", "sofia@cercanias.es"));

        Map<String, Object> perfil = controller.perfil("Bearer tok-123");

        assertEquals("sofia@cercanias.es", perfil.get("email"));
    }

    @Test
    void eliminarCuenta_borraCuentaYDevuelveConfirmacion() {
        Map<String, String> respuesta = controller.eliminarCuenta("Bearer tok-123");

        verify(servicioUsuarios).eliminarCuenta("Bearer tok-123");
        assertEquals("Cuenta y datos asociados eliminados permanentemente.", respuesta.get("mensaje"));
    }
}