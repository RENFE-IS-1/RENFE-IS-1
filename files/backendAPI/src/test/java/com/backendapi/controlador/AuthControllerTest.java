package com.backendapi.controlador;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "renfe.api.activo=false",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"
})
class AuthControllerTest {

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void prepararMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void registroLoginPerfilLogoutYEliminarCuenta() throws Exception {
        String email = "viajero_" + System.nanoTime() + "@cercanias.es";

        // 1. Registro público (US-013 -> siempre rol VIAJERO)
        String registroJson = """
                {
                  "nombre": "Laura Viajera",
                  "email": "%s",
                  "password": "Password123!",
                  "aceptaPrivacidad": true
                }
                """.formatted(email);

        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registroJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.email").value(email))
                .andExpect(jsonPath("$.usuario.rol").value("VIAJERO"));

        // 2. Registro duplicado falla
        mockMvc.perform(post("/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registroJson))
                .andExpect(status().is4xxClientError());

        // 3. Login con contraseña incorrecta falla
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "Incorrecta"}
                        """.formatted(email)))
                .andExpect(status().is4xxClientError());

        // 4. Login válido devuelve token
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "Password123!"}
                        """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        JsonNode loginNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String authHeader = "Bearer " + loginNode.get("token").asText();

        // 5. Consulta de perfil autenticado
        mockMvc.perform(get("/api/usuarios/me")
                .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // 6. Eliminación de cuenta (US-013 Criterio 8)
        mockMvc.perform(delete("/api/usuarios/me")
                .header("Authorization", authHeader))
                .andExpect(status().isOk());

        // 7. Tras eliminar la cuenta, el token ya no es válido
        mockMvc.perform(get("/api/usuarios/me")
                .header("Authorization", authHeader))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void cuentasInstitucionalesPrecargadasInicianSesionConSuRol() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "gestor@cercanias.es", "password": "Gestor1234!"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("GESTOR"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "analista@cercanias.es", "password": "Analista1234!"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("ANALISTA"));
    }
}