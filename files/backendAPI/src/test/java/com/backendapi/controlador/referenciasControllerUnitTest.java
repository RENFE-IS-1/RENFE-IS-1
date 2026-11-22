package com.backendapi.controlador;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.backendapi.controlador.PreferenciasController.FavoritaRequest;
import com.backendapi.controlador.PreferenciasController.RutaRequest;
import com.backendapi.modelo.EstacionFavorita;
import com.backendapi.modelo.RolUsuario;
import com.backendapi.modelo.RutaGuardada;
import com.backendapi.modelo.Usuario;
import com.backendapi.repositorio.EstacionFavoritaRepository;
import com.backendapi.repositorio.RutaGuardadaRepository;
import com.backendapi.servicio.ServicioUsuarios;

class PreferenciasControllerUnitTest {

    private ServicioUsuarios servicioUsuarios;
    private EstacionFavoritaRepository favoritaRepo;
    private RutaGuardadaRepository rutaRepo;
    private PreferenciasController controller;
    private Usuario viajero;

    @BeforeEach
    void preparar() {
        servicioUsuarios = mock(ServicioUsuarios.class);
        favoritaRepo = mock(EstacionFavoritaRepository.class);
        rutaRepo = mock(RutaGuardadaRepository.class);
        controller = new PreferenciasController(servicioUsuarios, favoritaRepo, rutaRepo);

        viajero = new Usuario("Elena", "elena@cercanias.es", "hash", RolUsuario.VIAJERO, true);
        ReflectionTestUtils.setField(viajero, "id", 7L);
        when(servicioUsuarios.obtenerUsuarioPorToken("Bearer token-ok")).thenReturn(viajero);
    }

    @Test
    void anadirFavorita_conStopIdEnBlanco_devuelveBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.anadirFavorita("Bearer token-ok", new FavoritaRequest("   ", "Atocha")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(favoritaRepo, never()).save(any());
    }

    @Test
    void anadirFavorita_yaExistente_noDuplicaRegistro() {
        EstacionFavorita existente = new EstacionFavorita("18000", "Atocha", viajero);
        when(favoritaRepo.findByUsuarioIdAndStopId(7L, "18000")).thenReturn(Optional.of(existente));

        EstacionFavorita resultado = controller.anadirFavorita(
                "Bearer token-ok", new FavoritaRequest("18000", "Atocha"));

        assertSame(existente, resultado);
        verify(favoritaRepo, never()).save(any());
    }

    @Test
    void guardarRuta_datosIncompletos_devuelveBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.guardarRuta("Bearer token-ok", new RutaRequest("", "Atocha", "Sol", "RAPIDA")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(rutaRepo, never()).save(any());
    }

    @Test
    void renombrarYEliminarRuta_deOtroUsuarioONoExistente_devuelveNotFound() {
        when(rutaRepo.findByIdAndUsuarioId(99L, 7L)).thenReturn(Optional.empty());

        ResponseStatusException errRenombrar = assertThrows(ResponseStatusException.class,
                () -> controller.renombrarRuta("Bearer token-ok", 99L, Map.of("nombre", "Nueva")));
        assertEquals(HttpStatus.NOT_FOUND, errRenombrar.getStatusCode());

        ResponseStatusException errEliminar = assertThrows(ResponseStatusException.class,
                () -> controller.eliminarRuta("Bearer token-ok", 99L));
        assertEquals(HttpStatus.NOT_FOUND, errEliminar.getStatusCode());
    }

    @Test
    void listarFavoritasYRutas_devuelveDatosDelUsuarioAutenticado() {
        when(favoritaRepo.findByUsuarioId(7L)).thenReturn(List.of(new EstacionFavorita("18000", "Atocha", viajero)));
        when(rutaRepo.findByUsuarioId(7L)).thenReturn(List.of(new RutaGuardada("Trabajo", "Atocha", "Nuevos Ministerios", "RAPIDA", viajero)));

        assertEquals(1, controller.listarFavoritas("Bearer token-ok").size());
        assertEquals(1, controller.listarRutas("Bearer token-ok").size());
    }
}