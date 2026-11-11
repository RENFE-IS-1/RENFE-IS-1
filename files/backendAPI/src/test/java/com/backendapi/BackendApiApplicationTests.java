package com.backendapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Sin descargar los datos en tiempo real de Renfe: el test no depende de la red
@SpringBootTest(properties = "renfe.api.activo=false")
class BackendApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
