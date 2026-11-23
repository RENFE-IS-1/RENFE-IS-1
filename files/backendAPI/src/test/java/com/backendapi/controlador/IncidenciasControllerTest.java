package com.backendapi.controlador;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests de integración de IncidenciasController: peticiones HTTP reales contra la aplicación completa.
 *
 * Usa una base de datos H2 en memoria propia (no toca data/usuarios_db) y desactiva la descarga de
 * Renfe, así que no necesita Internet. La línea C3 existe en los datos GTFS que carga la aplicación.
 * El gestor es la cuenta de demostración que crea ServicioUsuarios al arrancar.
 */
@SpringBootTest(properties = {
        "renfe.api.activo=false",
        "spring.datasource.url=jdbc:h2:mem:incidencias_controller_test;DB_CLOSE_DELAY=-1"
})
class IncidenciasControllerTest {

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    /** Prepara MockMvc sobre la aplicación arrancada antes de cada test. */
    @BeforeEach
    void prepararMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /** Inicia sesión y devuelve la cabecera Authorization; lo usan los tests que necesitan un usuario. */
    private String iniciarSesion(String email, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
        return "Bearer " + token;
    }

    /** Registra un viajero nuevo con un email único e inicia su sesión; devuelve la cabecera Authorization. */
    private String sesionDeViajeroNuevo() throws Exception {
        String email = "incidencias_" + System.nanoTime() + "@cercanias.es";
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Viajera", "email": "%s", "password": "Password123!", "aceptaPrivacidad": true}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        return iniciarSesion(email, "Password123!");
    }

    /** Sesión de la cuenta de gestor de demostración. */
    private String sesionDeGestor() throws Exception {
        return iniciarSesion("gestor@cercanias.es", "Gestor1234!");
    }

    /** Declara una incidencia con la sesión y el tipo dados; devuelve el resultado de la petición. */
    private MvcResult declarar(String auth, String linea, String tipo) throws Exception {
        return mockMvc.perform(post("/api/incidencias")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"linea": "%s", "tipo": "%s", "comentario": "Tren parado"}
                                """.formatted(linea, tipo)))
                .andReturn();
    }

    /** Un viajero declara una incidencia: 201 con el grupo, 1 declarante y confianza BAJA. */
    @Test
    void declarar_comoViajero_devuelve201ConLaIncidencia() throws Exception {
        mockMvc.perform(post("/api/incidencias")
                        .header("Authorization", sesionDeViajeroNuevo())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"linea": "C3", "tipo": "TREN_PARADO", "comentario": "Parado en Atocha"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.linea").value("C3"))
                .andExpect(jsonPath("$.tipo").value("TREN_PARADO"))
                .andExpect(jsonPath("$.origen").value("DECLARADA_POR_USUARIO"))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.numeroDeclarantes").value(1))
                .andExpect(jsonPath("$.nivelConfianza").value("BAJA"));
    }

    /** Dos viajeros distintos declaran lo mismo: queda una sola incidencia con 2 declarantes y confianza MEDIA. */
    @Test
    void declarar_dosViajeros_seAgrupanEnLaMismaIncidencia() throws Exception {
        MvcResult primera = declarar(sesionDeViajeroNuevo(), "C3", "ACCESIBILIDAD");
        long id = objectMapper.readTree(primera.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/incidencias")
                        .header("Authorization", sesionDeViajeroNuevo())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linea\": \"C3\", \"tipo\": \"ACCESIBILIDAD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.numeroDeclarantes").value(2))
                .andExpect(jsonPath("$.nivelConfianza").value("MEDIA"));
    }

    /** El mismo viajero declara dos veces lo mismo: la segunda vez recibe 409. */
    @Test
    void declarar_mismoViajeroDosVeces_devuelve409() throws Exception {
        String viajero = sesionDeViajeroNuevo();
        declarar(viajero, "C3", "SEGURIDAD");

        mockMvc.perform(post("/api/incidencias")
                        .header("Authorization", viajero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linea\": \"C3\", \"tipo\": \"SEGURIDAD\"}"))
                .andExpect(status().isConflict());
    }

    /** Sin sesión no se puede declarar: 401. */
    @Test
    void declarar_sinSesion_devuelve401() throws Exception {
        mockMvc.perform(post("/api/incidencias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linea\": \"C3\", \"tipo\": \"RETRASO\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Un gestor no puede declarar incidencias: 403. */
    @Test
    void declarar_comoGestor_devuelve403() throws Exception {
        mockMvc.perform(post("/api/incidencias")
                        .header("Authorization", sesionDeGestor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linea\": \"C3\", \"tipo\": \"RETRASO\"}"))
                .andExpect(status().isForbidden());
    }

    /** Tipo que no está en la lista: 400. */
    @Test
    void declarar_tipoNoValido_devuelve400() throws Exception {
        mockMvc.perform(post("/api/incidencias")
                        .header("Authorization", sesionDeViajeroNuevo())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"linea\": \"C3\", \"tipo\": \"INVENTADO\"}"))
                .andExpect(status().isBadRequest());
    }

    /** Un gestor cierra una incidencia declarada: 200 y queda finalizada. */
    @Test
    void finalizar_comoGestor_cierraLaIncidencia() throws Exception {
        MvcResult declarada = declarar(sesionDeViajeroNuevo(), "C3", "AGLOMERACION");
        long id = objectMapper.readTree(declarada.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/incidencias/" + id + "/finalizar")
                        .header("Authorization", sesionDeGestor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false))
                .andExpect(jsonPath("$.fin").exists());
    }

    /** Un viajero no puede cerrar incidencias: 403. */
    @Test
    void finalizar_comoViajero_devuelve403() throws Exception {
        String viajero = sesionDeViajeroNuevo();
        MvcResult declarada = declarar(viajero, "C3", "ESTACION_O_ACCESO_CERRADO");
        long id = objectMapper.readTree(declarada.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/incidencias/" + id + "/finalizar")
                        .header("Authorization", viajero))
                .andExpect(status().isForbidden());
    }

    /** La lista de tipos devuelve los 8 tipos, y OTRO indica que requiere especificación. */
    @Test
    void tipos_devuelveLaListaCerrada() throws Exception {
        mockMvc.perform(get("/api/incidencias/tipos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[7].codigo").value("OTRO"))
                .andExpect(jsonPath("$[7].requiereEspecificacion").value(true));
    }
}
