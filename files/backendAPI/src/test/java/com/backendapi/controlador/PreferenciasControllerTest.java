package com.backendapi.controlador;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

@SpringBootTest(properties = "renfe.api.activo=false")
class PreferenciasControllerTest {

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private String authHeader;

    @BeforeEach
    void registrarYAutenticarViajero() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        String email = "pref_" + System.nanoTime() + "@cercanias.es";

        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "nombre": "Carlos Cercanías",
                          "email": "%s",
                          "password": "Password123!",
                          "aceptaPrivacidad": true
                        }
                        """.formatted(email)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "Password123!"}
                        """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();
        authHeader = "Bearer " + token;
    }

    @Test
    void gestionarEstacionesFavoritas() throws Exception {
        // Añadir favorita (US-014)
        mockMvc.perform(post("/api/usuarios/favoritos")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"stopId": "18000", "nombreEstacion": "Atocha Cercanías"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stopId").value("18000"));

        // Listar favoritas
        mockMvc.perform(get("/api/usuarios/favoritos")
                .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombreEstacion").value("Atocha Cercanías"));

        // Eliminar favorita
        mockMvc.perform(delete("/api/usuarios/favoritos/18000")
                .header("Authorization", authHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/usuarios/favoritos")
                .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void gestionarRutasGuardadas() throws Exception {
        // Guardar ruta (US-015)
        MvcResult creada = mockMvc.perform(post("/api/usuarios/rutas")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "nombre": "Casa - Universidad",
                          "origen": "Atocha",
                          "destino": "Cantoblanco",
                          "criterio": "MAS_RAPIDA"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Casa - Universidad"))
                .andReturn();

        long rutaId = objectMapper.readTree(creada.getResponse().getContentAsString())
                .get("id").asLong();

        // Renombrar ruta
        mockMvc.perform(put("/api/usuarios/rutas/" + rutaId)
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre": "Ruta diaria"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ruta diaria"));

        // Eliminar ruta
        mockMvc.perform(delete("/api/usuarios/rutas/" + rutaId)
                .header("Authorization", authHeader))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/usuarios/rutas")
                .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}