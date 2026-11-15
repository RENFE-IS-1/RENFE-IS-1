package loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import modelo.GtfsRtJsonDto;
import repositorio.RedTransporte;

class ClienteApiRenfeTest {

    private static final String URL_POSICIONES = "https://renfe-pruebas.test/posiciones";
    private static final String FEED_VALIDO = """
            {"entity":[
              {"id":"valido","vehicle":{"trip":{"trip_id":"T1"},"position":{"latitude":40.4,"longitude":-3.7}}},
              {"id":"latitud_fuera","vehicle":{"position":{"latitude":95,"longitude":-3.7}}},
              {"id":"sin_longitud","vehicle":{"position":{"latitude":40.4}}},
              {"id":"sin_posicion","vehicle":{"trip":{"trip_id":"T2"}}}
            ]}
            """;

    @Test
    void publicaSoloPosicionesCompletasConCoordenadasValidas() {
        ClienteConServidor fixture = crearCliente();
        fixture.servidor.expect(requestTo(URL_POSICIONES))
                .andRespond(withSuccess(FEED_VALIDO, MediaType.APPLICATION_JSON));

        boolean actualizado = fixture.cliente.actualizarPosiciones(new RedTransporte());

        assertTrue(actualizado);
        List<GtfsRtJsonDto.VehiclePosition> posiciones = fixture.cliente.getPosicionesEnVivo();
        assertEquals(1, posiciones.size());
        assertEquals(40.4, posiciones.get(0).getPosition().getLatitude());
        assertEquals(-3.7, posiciones.get(0).getPosition().getLongitude());
        fixture.servidor.verify();
    }

    @Test
    void feedVacioEsValidoYEliminaLaListaAnterior() {
        ClienteConServidor fixture = crearCliente();
        fixture.servidor.expect(requestTo(URL_POSICIONES))
                .andRespond(withSuccess(FEED_VALIDO, MediaType.APPLICATION_JSON));
        fixture.servidor.expect(requestTo(URL_POSICIONES))
                .andRespond(withSuccess("{\"entity\":[]}", MediaType.APPLICATION_JSON));

        assertTrue(fixture.cliente.actualizarPosiciones(new RedTransporte()));
        assertTrue(fixture.cliente.actualizarPosiciones(new RedTransporte()));

        assertTrue(fixture.cliente.getPosicionesEnVivo().isEmpty());
        fixture.servidor.verify();
    }

    @Test
    void errorHttpDescartaPosicionesAnterioresParaNoMostrarlasComoActuales() {
        ClienteConServidor fixture = crearCliente();
        fixture.servidor.expect(requestTo(URL_POSICIONES))
                .andRespond(withSuccess(FEED_VALIDO, MediaType.APPLICATION_JSON));
        fixture.servidor.expect(requestTo(URL_POSICIONES)).andRespond(withServerError());

        assertTrue(fixture.cliente.actualizarPosiciones(new RedTransporte()));
        assertFalse(fixture.cliente.getPosicionesEnVivo().isEmpty());

        assertFalse(fixture.cliente.actualizarPosiciones(new RedTransporte()));
        assertTrue(fixture.cliente.getPosicionesEnVivo().isEmpty());
        fixture.servidor.verify();
    }

    @Test
    void jsonMalFormadoSeTrataComoFalloYNoConservaPosicionesAntiguas() {
        ClienteConServidor fixture = crearCliente();
        fixture.servidor.expect(requestTo(URL_POSICIONES))
                .andRespond(withSuccess(FEED_VALIDO, MediaType.APPLICATION_JSON));
        fixture.servidor.expect(requestTo(URL_POSICIONES))
                .andRespond(withSuccess("{json incorrecto", MediaType.APPLICATION_JSON));

        assertTrue(fixture.cliente.actualizarPosiciones(new RedTransporte()));
        assertFalse(fixture.cliente.actualizarPosiciones(new RedTransporte()));

        assertTrue(fixture.cliente.getPosicionesEnVivo().isEmpty());
        fixture.servidor.verify();
    }

    private static ClienteConServidor crearCliente() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(restTemplate).build();
        ClienteApiRenfe cliente = new ClienteApiRenfe(restTemplate);
        ReflectionTestUtils.setField(cliente, "urlPosiciones", URL_POSICIONES);
        return new ClienteConServidor(cliente, servidor);
    }

    private record ClienteConServidor(ClienteApiRenfe cliente, MockRestServiceServer servidor) {}
}
