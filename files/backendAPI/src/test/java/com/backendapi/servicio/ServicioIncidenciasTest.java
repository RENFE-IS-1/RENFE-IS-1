package com.backendapi.servicio;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.backendapi.modelo.DeclaracionIncidencia;
import com.backendapi.modelo.Incidencia;
import com.backendapi.modelo.NivelConfianza;
import com.backendapi.modelo.OrigenIncidencia;
import com.backendapi.modelo.RolUsuario;
import com.backendapi.modelo.TipoIncidenciaUsuario;
import com.backendapi.modelo.Usuario;
import com.backendapi.repositorio.DeclaracionIncidenciaRepository;
import com.backendapi.repositorio.IncidenciaRepository;

import modelo.AvisoOficial;
import modelo.Estacion;
import modelo.Linea;
import modelo.ParadaHorario;
import modelo.RetrasoParada;
import modelo.Viaje;
import repositorio.RedTransporte;

/**
 * Tests unitarios de ServicioIncidencias: detección por retrasos (US-017, criterios 2, 3 y 4), avisos
 * oficiales de Renfe (criterio 1) e incidencias declaradas por viajeros (agrupación, nivel de
 * confianza y cierre por un gestor).
 *
 * Usa una RedTransporte de prueba con trenes inventados y un repositorio simulado (mock), así que no
 * necesita base de datos ni conexión a Internet.
 */
class ServicioIncidenciasTest {

    /** Hora fija de la revisión para que los tests no dependan de la hora a la que se ejecutan. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 23, 8, 0);

    private RedTransporte red;
    private IncidenciaRepository repositorio;
    private DeclaracionIncidenciaRepository declaraciones;
    private ServicioIncidencias servicio;

    /** Prepara una red con las líneas C3 y C4, datos en tiempo real disponibles y repositorios simulados. */
    @BeforeEach
    void preparar() {
        red = new RedTransporte();
        red.addLinea(new Linea("R_C3", "C3", "Aranjuez - Chamartín", "8F2D86"));
        red.addLinea(new Linea("R_C4", "C4", "Parla - Colmenar Viejo", "2C5AA0"));
        red.setHorariosTiempoRealActivos(true);

        repositorio = mock(IncidenciaRepository.class);
        declaraciones = mock(DeclaracionIncidenciaRepository.class);
        servicio = new ServicioIncidencias(repositorio, declaraciones, red);
    }

    /**
     * Crea en la red un tren de la línea indicada con el retraso indicado (en segundos).
     * Lo usan los tests para preparar los retrasos de cada caso.
     */
    private void anadirTrenes(Map<String, List<RetrasoParada>> retrasos, String routeId, int cantidad, int retrasoSegundos) {
        for (int i = 0; i < cantidad; i++) {
            String tripId = routeId + "_tren_" + retrasos.size();
            red.addViaje(new Viaje(tripId, routeId, "SVC"));
            retrasos.put(tripId, List.of(RetrasoParada.deTodoElViaje(retrasoSegundos)));
        }
    }

    /** Devuelve la única incidencia que el servicio ha guardado; lo usan los tests que comprueban qué se guarda. */
    private Incidencia incidenciaGuardada() {
        ArgumentCaptor<Incidencia> captor = ArgumentCaptor.forClass(Incidencia.class);
        verify(repositorio, times(1)).save(captor.capture());
        return captor.getValue();
    }

    // --- Criterio 2: crear la incidencia ---

    /** 3 trenes de la C3 con 10 minutos justos de retraso: se crea una incidencia por retraso en la C3. */
    @Test
    void tresTrenesConDiezMinutos_creaIncidenciaDeRetraso() {
        Map<String, List<RetrasoParada>> retrasos = new HashMap<>();
        anadirTrenes(retrasos, "R_C3", 3, 600);
        red.reemplazarRetrasos(retrasos);

        servicio.detectarIncidenciasPorRetraso(AHORA);

        Incidencia creada = incidenciaGuardada();
        assertEquals("C3", creada.getLinea());
        assertEquals(ServicioIncidencias.TIPO_RETRASO, creada.getTipo());
        assertEquals(OrigenIncidencia.DETECCION_PROPIA, creada.getOrigen());
        assertEquals(AHORA, creada.getInicio());
        assertNull(creada.getIdExterno());
        assertTrue(creada.isActiva());
        assertTrue(creada.getDescripcion().startsWith("3 trenes"));
    }

    /** Solo 2 trenes retrasados en la línea: no llega al mínimo y no se crea nada. */
    @Test
    void soloDosTrenesRetrasados_noCreaIncidencia() {
        Map<String, List<RetrasoParada>> retrasos = new HashMap<>();
        anadirTrenes(retrasos, "R_C3", 2, 900);
        red.reemplazarRetrasos(retrasos);

        servicio.detectarIncidenciasPorRetraso(AHORA);

        verify(repositorio, never()).save(any());
    }

    /** 3 trenes con 9 min 59 s de retraso: no cuentan como retrasados y no se crea nada. */
    @Test
    void retrasoMenorDeDiezMinutos_noCuenta() {
        Map<String, List<RetrasoParada>> retrasos = new HashMap<>();
        anadirTrenes(retrasos, "R_C3", 3, 599);
        red.reemplazarRetrasos(retrasos);

        servicio.detectarIncidenciasPorRetraso(AHORA);

        verify(repositorio, never()).save(any());
    }

    /** 2 trenes retrasados en la C3 y 2 en la C4: no se suman entre líneas distintas, no se crea nada. */
    @Test
    void trenesDeLineasDistintas_noSeMezclan() {
        Map<String, List<RetrasoParada>> retrasos = new HashMap<>();
        anadirTrenes(retrasos, "R_C3", 2, 900);
        anadirTrenes(retrasos, "R_C4", 2, 900);
        red.reemplazarRetrasos(retrasos);

        servicio.detectarIncidenciasPorRetraso(AHORA);

        verify(repositorio, never()).save(any());
    }

    /** Trenes cuyo trip_id no está en los horarios GTFS: la línea se saca del propio trip_id. */
    @Test
    void tripIdNoEstaEnLosHorarios_obtieneLaLineaDelTripId() {
        red.reemplazarRetrasos(Map.of(
                "5001_C5_X", List.of(RetrasoParada.deTodoElViaje(700)),
                "5002_C5_X", List.of(RetrasoParada.deTodoElViaje(700)),
                "5003_C5_X", List.of(RetrasoParada.deTodoElViaje(700))));

        servicio.detectarIncidenciasPorRetraso(AHORA);

        assertEquals("C5", incidenciaGuardada().getLinea());
    }

    // --- Criterio 3: no duplicar ---

    /** La incidencia de la C3 ya existía y se sigue detectando: no se crea otra, se actualiza la existente. */
    @Test
    void incidenciaYaActiva_noSeDuplicaYSeActualiza() {
        Map<String, List<RetrasoParada>> retrasos = new HashMap<>();
        anadirTrenes(retrasos, "R_C3", 4, 900);
        red.reemplazarRetrasos(retrasos);

        LocalDateTime inicioOriginal = AHORA.minusMinutes(5);
        Incidencia existente = new Incidencia("C3", ServicioIncidencias.TIPO_RETRASO, "3 trenes...",
                OrigenIncidencia.DETECCION_PROPIA, null, inicioOriginal);
        when(repositorio.findByLineaAndOrigenAndFinIsNull("C3", OrigenIncidencia.DETECCION_PROPIA))
                .thenReturn(Optional.of(existente));
        when(repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.DETECCION_PROPIA))
                .thenReturn(List.of(existente));

        servicio.detectarIncidenciasPorRetraso(AHORA);

        assertSame(existente, incidenciaGuardada());
        assertEquals(inicioOriginal, existente.getInicio());
        assertEquals(AHORA, existente.getUltimaActualizacion());
        assertTrue(existente.getDescripcion().startsWith("4 trenes"));
        assertTrue(existente.isActiva());
    }

    // --- Criterio 4: cerrar la incidencia ---

    /** La C3 tenía una incidencia activa y ya no hay trenes retrasados: se cierra con la hora de la revisión. */
    @Test
    void dejaDeDetectarse_seCierraConSuHoraDeFin() {
        red.reemplazarRetrasos(Map.of());
        Incidencia activa = new Incidencia("C3", ServicioIncidencias.TIPO_RETRASO, "3 trenes...",
                OrigenIncidencia.DETECCION_PROPIA, null, AHORA.minusMinutes(30));
        when(repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.DETECCION_PROPIA))
                .thenReturn(List.of(activa));

        servicio.detectarIncidenciasPorRetraso(AHORA);

        assertSame(activa, incidenciaGuardada());
        assertFalse(activa.isActiva());
        assertEquals(AHORA, activa.getFin());
    }

    /** La C3 baja a 2 trenes retrasados: ya no cumple la condición y su incidencia se cierra. */
    @Test
    void bajaDelMinimoDeTrenes_seCierra() {
        Map<String, List<RetrasoParada>> retrasos = new HashMap<>();
        anadirTrenes(retrasos, "R_C3", 2, 900);
        red.reemplazarRetrasos(retrasos);
        Incidencia activa = new Incidencia("C3", ServicioIncidencias.TIPO_RETRASO, "3 trenes...",
                OrigenIncidencia.DETECCION_PROPIA, null, AHORA.minusMinutes(30));
        when(repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.DETECCION_PROPIA))
                .thenReturn(List.of(activa));

        servicio.detectarIncidenciasPorRetraso(AHORA);

        assertEquals(AHORA, activa.getFin());
    }

    // --- Fallo de la API ---

    /** Si la última descarga de retrasos ha fallado, no se crea ni se cierra nada. */
    @Test
    void sinDatosDeTiempoReal_noCambiaNada() {
        red.setHorariosTiempoRealActivos(false);
        Map<String, List<RetrasoParada>> retrasos = new HashMap<>();
        anadirTrenes(retrasos, "R_C3", 3, 900);
        red.reemplazarRetrasos(retrasos);

        servicio.detectarIncidenciasPorRetraso(AHORA);

        verifyNoInteractions(repositorio);
    }

    /** Una incidencia declarada por viajeros activa no la cierra la detección por retrasos. */
    @Test
    void deteccionPorRetrasos_noCierraLasDeclaradasPorViajeros() {
        red.reemplazarRetrasos(Map.of());
        Incidencia declarada = new Incidencia("C3", "AVERIA_TREN", "Avería en el tren",
                OrigenIncidencia.DECLARADA_POR_USUARIO, null, AHORA.minusMinutes(30));
        when(repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.DETECCION_PROPIA)).thenReturn(List.of());

        servicio.detectarIncidenciasPorRetraso(AHORA);

        verify(repositorio, never()).findByOrigenAndFinIsNull(OrigenIncidencia.DECLARADA_POR_USUARIO);
        assertTrue(declarada.isActiva());
    }

    // =====================================================================
    // Incidencias declaradas por viajeros
    // =====================================================================

    /** Crea un usuario de prueba con un id y un rol; lo usan los tests de incidencias declaradas. */
    private static Usuario usuario(long id, RolUsuario rol) {
        Usuario u = new Usuario("Usuario " + id, "usuario" + id + "@cercanias.es", "hash", rol, true);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    /** Hace que el repositorio simulado devuelva la misma incidencia que recibe al guardar, como el real. */
    private void guardarDevuelveLaMismaIncidencia() {
        when(repositorio.save(any(Incidencia.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Crea un grupo ya existente de declaraciones en la C3 con un id y un número de viajeros dado. */
    private Incidencia grupoExistente(long id, int viajeros) {
        Incidencia grupo = new Incidencia("C3", "AVERIA_TREN", "Avería en el tren",
                OrigenIncidencia.DECLARADA_POR_USUARIO, null, AHORA.minusMinutes(10));
        ReflectionTestUtils.setField(grupo, "id", id);
        for (int i = 0; i < viajeros; i++) grupo.sumarDeclarante(AHORA.minusMinutes(10));
        when(repositorio.findByLineaAndTipoAndOrigenAndFinIsNull("C3", "AVERIA_TREN", OrigenIncidencia.DECLARADA_POR_USUARIO))
                .thenReturn(Optional.of(grupo));
        return grupo;
    }

    /** Primera declaración en una línea: se crea un grupo nuevo con 1 viajero y confianza BAJA, y se guarda la declaración. */
    @Test
    void declarar_primeraDeclaracion_creaGrupoConConfianzaBaja() {
        guardarDevuelveLaMismaIncidencia();
        Usuario viajero = usuario(1, RolUsuario.VIAJERO);

        Incidencia grupo = servicio.declararIncidencia(viajero, "c3", TipoIncidenciaUsuario.AVERIA_TREN,
                null, "  Tren parado en Atocha  ", AHORA);

        assertEquals("C3", grupo.getLinea());
        assertEquals("AVERIA_TREN", grupo.getTipo());
        assertEquals(OrigenIncidencia.DECLARADA_POR_USUARIO, grupo.getOrigen());
        assertEquals(1, grupo.getNumeroDeclarantes());
        assertEquals(NivelConfianza.BAJA, grupo.getNivelConfianza());
        assertEquals(AHORA, grupo.getInicio());
        assertTrue(grupo.isActiva());

        ArgumentCaptor<DeclaracionIncidencia> captor = ArgumentCaptor.forClass(DeclaracionIncidencia.class);
        verify(declaraciones).save(captor.capture());
        assertSame(grupo, captor.getValue().getIncidencia());
        assertSame(viajero, captor.getValue().getUsuario());
        assertEquals("Tren parado en Atocha", captor.getValue().getComentario());
    }

    /** Un segundo viajero declara lo mismo: se suma al grupo existente (no se crea otro) y la confianza pasa a MEDIA. */
    @Test
    void declarar_segundoViajero_seSumaAlGrupoConConfianzaMedia() {
        guardarDevuelveLaMismaIncidencia();
        Incidencia grupo = grupoExistente(50, 1);

        Incidencia resultado = servicio.declararIncidencia(usuario(2, RolUsuario.VIAJERO), "C3",
                TipoIncidenciaUsuario.AVERIA_TREN, null, null, AHORA);

        assertSame(grupo, resultado);
        assertEquals(2, grupo.getNumeroDeclarantes());
        assertEquals(NivelConfianza.MEDIA, grupo.getNivelConfianza());
        assertEquals(AHORA, grupo.getUltimaActualizacion());
        assertEquals(AHORA.minusMinutes(10), grupo.getInicio());
    }

    /** Un tercer viajero distinto: la incidencia pasa a confianza ALTA (confirmada). */
    @Test
    void declarar_tercerViajero_confianzaAlta() {
        guardarDevuelveLaMismaIncidencia();
        Incidencia grupo = grupoExistente(50, 2);

        servicio.declararIncidencia(usuario(3, RolUsuario.VIAJERO), "C3",
                TipoIncidenciaUsuario.AVERIA_TREN, null, null, AHORA);

        assertEquals(3, grupo.getNumeroDeclarantes());
        assertEquals(NivelConfianza.ALTA, grupo.getNivelConfianza());
    }

    /** El mismo viajero intenta declarar dos veces la misma incidencia: 409 y el contador no cambia. */
    @Test
    void declarar_mismoViajeroDosVeces_conflictoYNoCuentaDosVeces() {
        Incidencia grupo = grupoExistente(50, 1);
        when(declaraciones.existsByIncidenciaIdAndUsuarioId(50L, 1L)).thenReturn(true);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                servicio.declararIncidencia(usuario(1, RolUsuario.VIAJERO), "C3",
                        TipoIncidenciaUsuario.AVERIA_TREN, null, null, AHORA));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertEquals(1, grupo.getNumeroDeclarantes());
        verify(declaraciones, never()).save(any());
    }

    /** Otro tipo en la misma línea: no se mezcla con el grupo de AVERIA_TREN, se crea un grupo nuevo. */
    @Test
    void declarar_otroTipoEnLaMismaLinea_creaOtroGrupo() {
        guardarDevuelveLaMismaIncidencia();
        Incidencia averia = grupoExistente(50, 2);

        Incidencia aglomeracion = servicio.declararIncidencia(usuario(4, RolUsuario.VIAJERO), "C3",
                TipoIncidenciaUsuario.AGLOMERACION, null, null, AHORA);

        assertNotSame(averia, aglomeracion);
        assertEquals("AGLOMERACION", aglomeracion.getTipo());
        assertEquals(1, aglomeracion.getNumeroDeclarantes());
        assertEquals(2, averia.getNumeroDeclarantes());
    }

    /** Tipo OTRO con el texto especificado: se acepta y la declaración guarda ese texto. */
    @Test
    void declarar_tipoOtroConEspecificacion_guardaElTexto() {
        guardarDevuelveLaMismaIncidencia();

        servicio.declararIncidencia(usuario(1, RolUsuario.VIAJERO), "C4", TipoIncidenciaUsuario.OTRO,
                "Puerta del vagón bloqueada", null, AHORA);

        ArgumentCaptor<DeclaracionIncidencia> captor = ArgumentCaptor.forClass(DeclaracionIncidencia.class);
        verify(declaraciones).save(captor.capture());
        assertEquals("Puerta del vagón bloqueada", captor.getValue().getEspecificacion());
    }

    /** Tipo OTRO sin especificar qué ocurre: 400 y no se guarda nada. */
    @Test
    void declarar_tipoOtroSinEspecificacion_error400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                servicio.declararIncidencia(usuario(1, RolUsuario.VIAJERO), "C3",
                        TipoIncidenciaUsuario.OTRO, "   ", null, AHORA));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(repositorio, never()).save(any());
    }

    /** Sin tipo: 400. */
    @Test
    void declarar_sinTipo_error400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                servicio.declararIncidencia(usuario(1, RolUsuario.VIAJERO), "C3", null, null, null, AHORA));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    /** Línea que no existe en la red: 400 y no se guarda nada. */
    @Test
    void declarar_lineaInexistente_error400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                servicio.declararIncidencia(usuario(1, RolUsuario.VIAJERO), "C99",
                        TipoIncidenciaUsuario.RETRASO, null, null, AHORA));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(repositorio, never()).save(any());
    }

    /** Un gestor o un analista no pueden declarar incidencias: 403. */
    @Test
    void declarar_usuarioQueNoEsViajero_error403() {
        for (RolUsuario rol : List.of(RolUsuario.GESTOR, RolUsuario.ANALISTA)) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                    servicio.declararIncidencia(usuario(9, rol), "C3",
                            TipoIncidenciaUsuario.RETRASO, null, null, AHORA));
            assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        }
        verify(repositorio, never()).save(any());
    }

    // --- Cierre por un gestor ---

    /** Un gestor cierra una incidencia declarada: queda finalizada con la hora indicada. */
    @Test
    void finalizar_gestor_cierraLaIncidenciaDeclarada() {
        guardarDevuelveLaMismaIncidencia();
        Incidencia grupo = grupoExistente(50, 3);
        when(repositorio.findById(50L)).thenReturn(Optional.of(grupo));

        Incidencia cerrada = servicio.finalizarIncidenciaDeclarada(usuario(7, RolUsuario.GESTOR), 50L, AHORA);

        assertFalse(cerrada.isActiva());
        assertEquals(AHORA, cerrada.getFin());
    }

    /** Un viajero no puede cerrar incidencias: 403. */
    @Test
    void finalizar_viajero_error403() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                servicio.finalizarIncidenciaDeclarada(usuario(1, RolUsuario.VIAJERO), 50L, AHORA));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    /** Incidencia que no existe: 404. */
    @Test
    void finalizar_incidenciaInexistente_error404() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                servicio.finalizarIncidenciaDeclarada(usuario(7, RolUsuario.GESTOR), 99L, AHORA));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    /** Una incidencia de Renfe o de detección propia no se cierra a mano: 400 y sigue activa. */
    @Test
    void finalizar_incidenciaQueNoEsDeclarada_error400() {
        Incidencia retraso = new Incidencia("C3", ServicioIncidencias.TIPO_RETRASO, "3 trenes...",
                OrigenIncidencia.DETECCION_PROPIA, null, AHORA.minusMinutes(5));
        when(repositorio.findById(60L)).thenReturn(Optional.of(retraso));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                servicio.finalizarIncidenciaDeclarada(usuario(7, RolUsuario.GESTOR), 60L, AHORA));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertTrue(retraso.isActiva());
    }

    // --- Casos límite añadidos en la revisión del código ---

    /**
     * Trenes de la C4a: uno con trip_id de los horarios GTFS y dos con un trip_id desconocido. Los tres
     * deben contar para la misma línea "C4a" (antes los de trip_id desconocido iban a "C4A" y no se
     * llegaba al mínimo de 3 en ninguna de las dos).
     */
    @Test
    void ramalConLetra_tripIdConocidoYDesconocido_cuentanEnLaMismaLinea() {
        red.addLinea(new Linea("R_C4a", "C4a", "Alcobendas - Parla", "2C5AA0"));
        red.addViaje(new Viaje("1001J1C4a", "R_C4a", "SVC"));
        red.reemplazarRetrasos(Map.of(
                "1001J1C4a", List.of(RetrasoParada.deTodoElViaje(700)),
                "9002X2C4a", List.of(RetrasoParada.deTodoElViaje(700)),
                "9003X3C4A", List.of(RetrasoParada.deTodoElViaje(700))));

        servicio.detectarIncidenciasPorRetraso(AHORA);

        assertEquals("C4a", incidenciaGuardada().getLinea());
    }

    /** El nombre sacado del trip_id se escribe como en los horarios: C mayúscula, letra de ramal en minúscula. */
    @Test
    void nombreComercial_normalizaMayusculas() {
        assertEquals("C4a", ServicioIncidencias.nombreComercial("C4A"));
        assertEquals("C8b", ServicioIncidencias.nombreComercial("c8B"));
        assertEquals("C10", ServicioIncidencias.nombreComercial("c10"));
    }

    /** Textos más largos que sus columnas: 400 antes de tocar la base de datos (antes daba 500). */
    @Test
    void declarar_textosDemasiadoLargos_error400SinGuardarNada() {
        ResponseStatusException especificacion = assertThrows(ResponseStatusException.class, () ->
                servicio.declararIncidencia(usuario(1, RolUsuario.VIAJERO), "C3", TipoIncidenciaUsuario.OTRO,
                        "x".repeat(ServicioIncidencias.MAX_ESPECIFICACION + 1), null, AHORA));
        ResponseStatusException comentario = assertThrows(ResponseStatusException.class, () ->
                servicio.declararIncidencia(usuario(1, RolUsuario.VIAJERO), "C3", TipoIncidenciaUsuario.RETRASO,
                        null, "x".repeat(ServicioIncidencias.MAX_COMENTARIO + 1), AHORA));

        assertEquals(HttpStatus.BAD_REQUEST, especificacion.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, comentario.getStatusCode());
        verify(repositorio, never()).save(any());
        verify(declaraciones, never()).save(any());
    }

    /** Justo en el límite sí se acepta. */
    @Test
    void declarar_textosEnElLimite_seAceptan() {
        guardarDevuelveLaMismaIncidencia();

        Incidencia grupo = servicio.declararIncidencia(usuario(1, RolUsuario.VIAJERO), "C3", TipoIncidenciaUsuario.OTRO,
                "x".repeat(ServicioIncidencias.MAX_ESPECIFICACION), "y".repeat(ServicioIncidencias.MAX_COMENTARIO), AHORA);

        assertEquals(1, grupo.getNumeroDeclarantes());
    }

    /**
     * Doble clic: la comprobación previa no ve la primera declaración, pero la base de datos rechaza la
     * segunda por la restricción única. Debe responder 409, no 500.
     */
    @Test
    void declarar_dobleEnvioSimultaneo_conflictoEnVezDeError500() {
        guardarDevuelveLaMismaIncidencia();
        grupoExistente(50, 1);
        when(declaraciones.existsByIncidenciaIdAndUsuarioId(50L, 2L)).thenReturn(false);
        when(declaraciones.save(any(DeclaracionIncidencia.class)))
                .thenThrow(new DataIntegrityViolationException("unique (incidencia_id, usuario_id)"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                servicio.declararIncidencia(usuario(2, RolUsuario.VIAJERO), "C3",
                        TipoIncidenciaUsuario.AVERIA_TREN, null, null, AHORA));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    /** Los textos solo con espacios cuentan como vacíos: OTRO con especificación "   " es un 400. */
    @Test
    void declarar_tipoOtroConEspecificacionEnBlanco_error400() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                servicio.declararIncidencia(usuario(1, RolUsuario.VIAJERO), "C3", TipoIncidenciaUsuario.OTRO,
                        "   ", null, AHORA));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    // =====================================================================
    // Avisos oficiales de Renfe (US-017, criterio 1)
    // =====================================================================

    /** Hora de inicio de los avisos de prueba: una hora antes de la revisión, en segundos UTC como los manda Renfe. */
    private static final long INICIO_AVISO = AHORA.minusHours(1).atZone(ZoneId.of("Europe/Madrid")).toEpochSecond();

    /**
     * Añade a la red las rutas con los route_id reales de Renfe para C3 y C4 y marca la descarga de
     * alertas como correcta. Lo usan los tests de avisos oficiales.
     */
    private void prepararRutasDeRenfe() {
        red.addLinea(new Linea("10T0007C3", "C3", "Chamartín - Aranjuez", "952585"));
        red.addLinea(new Linea("10T0011C4", "C4", "Parla - Colmenar Viejo", "2C5AA0"));
        red.setIncidenciasTiempoRealActivas(true);
    }

    /** Aviso de prueba como el del feed real: afecta a C3, C4 y una ruta de otro núcleo (Asturias). */
    private static AvisoOficial avisoAtocha() {
        return new AvisoOficial("AVISO_528848", null,
                "#MadC3 #MadC4 Debido a una acumulación de trenes en la estación de Atocha.",
                Set.of("10T0007C3", "10T0011C4", "20T0001C1"), Set.of(), INICIO_AVISO);
    }

    /** Devuelve todas las incidencias que el servicio ha guardado; lo usan los tests de avisos. */
    private List<Incidencia> incidenciasGuardadas(int veces) {
        ArgumentCaptor<Incidencia> captor = ArgumentCaptor.forClass(Incidencia.class);
        verify(repositorio, times(veces)).save(captor.capture());
        return captor.getAllValues();
    }

    /** Un aviso de C3 y C4: se crea una incidencia por línea, con el id del aviso, su hora de inicio y sin hashtags. */
    @Test
    void avisos_avisoDeDosLineas_creaUnaIncidenciaPorLinea() {
        prepararRutasDeRenfe();
        red.reemplazarAvisosOficiales(List.of(avisoAtocha()));

        servicio.registrarAvisosOficiales(AHORA);

        List<Incidencia> creadas = incidenciasGuardadas(2);
        assertEquals(Set.of("C3", "C4"), Set.of(creadas.get(0).getLinea(), creadas.get(1).getLinea()));
        for (Incidencia i : creadas) {
            assertEquals(OrigenIncidencia.AVISO_OFICIAL, i.getOrigen());
            assertEquals(ServicioIncidencias.TIPO_AVISO_OFICIAL, i.getTipo());
            assertEquals("AVISO_528848", i.getIdExterno());
            assertEquals(AHORA.minusHours(1), i.getInicio());
            assertEquals("Debido a una acumulación de trenes en la estación de Atocha.", i.getDescripcion());
            assertTrue(i.isActiva());
        }
    }

    /** El aviso sigue en el feed y ya estaba registrado: no se duplica, se actualiza la existente. */
    @Test
    void avisos_avisoYaRegistrado_noSeDuplicaYSeActualiza() {
        prepararRutasDeRenfe();
        red.reemplazarAvisosOficiales(List.of(new AvisoOficial("AVISO_1", null, "Texto nuevo",
                Set.of("10T0007C3"), Set.of(), INICIO_AVISO)));
        Incidencia existente = new Incidencia("C3", ServicioIncidencias.TIPO_AVISO_OFICIAL, "Texto viejo",
                OrigenIncidencia.AVISO_OFICIAL, "AVISO_1", AHORA.minusHours(1));
        when(repositorio.findByIdExternoAndLineaAndFinIsNull("AVISO_1", "C3")).thenReturn(Optional.of(existente));
        when(repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.AVISO_OFICIAL)).thenReturn(List.of(existente));

        servicio.registrarAvisosOficiales(AHORA);

        assertSame(existente, incidenciasGuardadas(1).get(0));
        assertEquals("Texto nuevo", existente.getDescripcion());
        assertEquals(AHORA, existente.getUltimaActualizacion());
        assertTrue(existente.isActiva());
    }

    /** El aviso ya no está en el feed: su incidencia se cierra con la hora de la revisión. */
    @Test
    void avisos_avisoQueSaleDelFeed_seCierra() {
        prepararRutasDeRenfe();
        red.reemplazarAvisosOficiales(List.of());
        Incidencia activa = new Incidencia("C3", ServicioIncidencias.TIPO_AVISO_OFICIAL, "Aviso",
                OrigenIncidencia.AVISO_OFICIAL, "AVISO_1", AHORA.minusHours(2));
        when(repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.AVISO_OFICIAL)).thenReturn(List.of(activa));

        servicio.registrarAvisosOficiales(AHORA);

        assertFalse(activa.isActiva());
        assertEquals(AHORA, activa.getFin());
    }

    /** El aviso deja de afectar a una línea: se cierra la incidencia de esa línea y la de la otra sigue. */
    @Test
    void avisos_avisoQueDejaDeAfectarAUnaLinea_cierraSoloEsaLinea() {
        prepararRutasDeRenfe();
        red.reemplazarAvisosOficiales(List.of(new AvisoOficial("AVISO_1", null, "Aviso",
                Set.of("10T0007C3"), Set.of(), INICIO_AVISO)));
        Incidencia c3 = new Incidencia("C3", ServicioIncidencias.TIPO_AVISO_OFICIAL, "Aviso",
                OrigenIncidencia.AVISO_OFICIAL, "AVISO_1", AHORA.minusHours(1));
        Incidencia c4 = new Incidencia("C4", ServicioIncidencias.TIPO_AVISO_OFICIAL, "Aviso",
                OrigenIncidencia.AVISO_OFICIAL, "AVISO_1", AHORA.minusHours(1));
        when(repositorio.findByIdExternoAndLineaAndFinIsNull("AVISO_1", "C3")).thenReturn(Optional.of(c3));
        when(repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.AVISO_OFICIAL)).thenReturn(List.of(c3, c4));

        servicio.registrarAvisosOficiales(AHORA);

        assertTrue(c3.isActiva());
        assertFalse(c4.isActiva());
    }

    /** Si la última descarga de alertas ha fallado, no se crea ni se cierra nada. */
    @Test
    void avisos_descargaFallida_noCambiaNada() {
        prepararRutasDeRenfe();
        red.setIncidenciasTiempoRealActivas(false);
        red.reemplazarAvisosOficiales(List.of(avisoAtocha()));

        servicio.registrarAvisosOficiales(AHORA);

        verifyNoInteractions(repositorio);
    }

    /** Un aviso solo de otros núcleos (rutas que no están en la red de Madrid) no crea ninguna incidencia. */
    @Test
    void avisos_rutasDeOtrosNucleos_seIgnoran() {
        prepararRutasDeRenfe();
        red.reemplazarAvisosOficiales(List.of(new AvisoOficial("AVISO_ASTURIAS", null, "Aviso en Asturias",
                Set.of("20T0001C1", "20T0002C1"), Set.of(), INICIO_AVISO)));

        servicio.registrarAvisosOficiales(AHORA);

        verify(repositorio, never()).save(any());
    }

    /** Un aviso sin rutas pero con una estación: se registra en las líneas que pasan por esa estación. */
    @Test
    void avisos_soloConEstacion_usaLasLineasDeLaEstacion() {
        prepararRutasDeRenfe();
        red.addEstacion(new Estacion("18000", "Atocha", 40.4065, -3.6908));
        red.addViaje(new Viaje("V1", "10T0007C3", "SVC"));
        red.addParadaHorario(new ParadaHorario("V1", 28800, 28800, "18000", 1));
        red.reemplazarAvisosOficiales(List.of(new AvisoOficial("AVISO_2", null, "Ascensor averiado en Atocha",
                Set.of(), Set.of("18000"), INICIO_AVISO)));

        servicio.registrarAvisosOficiales(AHORA);

        assertEquals("C3", incidenciasGuardadas(1).get(0).getLinea());
    }

    /** Un aviso con hora de inicio futura (obras anunciadas) todavía no se registra. */
    @Test
    void avisos_inicioFuturo_todaviaNoSeRegistra() {
        prepararRutasDeRenfe();
        long manana = AHORA.plusDays(1).atZone(ZoneId.of("Europe/Madrid")).toEpochSecond();
        red.reemplazarAvisosOficiales(List.of(new AvisoOficial("AVISO_OBRAS", null, "Obras mañana",
                Set.of("10T0007C3"), Set.of(), manana)));

        servicio.registrarAvisosOficiales(AHORA);

        verify(repositorio, never()).save(any());
    }

    /** Un aviso sin hora de inicio se registra con la hora de la revisión. */
    @Test
    void avisos_sinHoraDeInicio_usaLaHoraDeLaRevision() {
        prepararRutasDeRenfe();
        red.reemplazarAvisosOficiales(List.of(new AvisoOficial("AVISO_3", null, "Aviso",
                Set.of("10T0007C3"), Set.of(), null)));

        servicio.registrarAvisosOficiales(AHORA);

        assertEquals(AHORA, incidenciasGuardadas(1).get(0).getInicio());
    }

    /** La detección de avisos no cierra incidencias de otros orígenes (retrasos o declaradas). */
    @Test
    void avisos_noTocanOtrosOrigenes() {
        prepararRutasDeRenfe();
        red.reemplazarAvisosOficiales(List.of());

        servicio.registrarAvisosOficiales(AHORA);

        verify(repositorio, never()).findByOrigenAndFinIsNull(OrigenIncidencia.DETECCION_PROPIA);
        verify(repositorio, never()).findByOrigenAndFinIsNull(OrigenIncidencia.DECLARADA_POR_USUARIO);
    }

    /** Texto del aviso: se quitan los hashtags iniciales, se antepone el título si lo hay y se recorta si es muy largo. */
    @Test
    void descripcionDeAviso_limpiaHashtagsTituloYLongitud() {
        assertEquals("Retrasos en Atocha", ServicioIncidencias.descripcionDeAviso(new AvisoOficial(
                "A", null, "  #MadC3 #MadC4   Retrasos en Atocha", Set.of(), Set.of(), null)));
        assertEquals("Obras: Corte de vía", ServicioIncidencias.descripcionDeAviso(new AvisoOficial(
                "A", "Obras", "Corte de vía", Set.of(), Set.of(), null)));
        assertEquals("Aviso oficial de Renfe", ServicioIncidencias.descripcionDeAviso(new AvisoOficial(
                "A", null, null, Set.of(), Set.of(), null)));
        assertEquals(ServicioIncidencias.MAX_DESCRIPCION, ServicioIncidencias.descripcionDeAviso(new AvisoOficial(
                "A", null, "x".repeat(3000), Set.of(), Set.of(), null)).length());
    }
}
