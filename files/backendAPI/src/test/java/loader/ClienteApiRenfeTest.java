package loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import modelo.AvisoOficial;
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

    // --- Alertas (US-017): avisos oficiales con sus rutas y su hora de inicio ---

    private static final String URL_INCIDENCIAS = "https://renfe-pruebas.test/alertas";

    /**
     * Copia recortada del feed real de alertas de Renfe (gtfsrt.renfe.com/alerts.json, 27/11/2026):
     * claves en camelCase, rutas en informedEntity, inicio en activePeriod como texto y sin headerText.
     * Incluye un id repetido (el feed real lo hace) y un aviso que solo indica una estación.
     */
    private static final String FEED_ALERTAS = """
            {"header":{"gtfsRealtimeVersion":"2.0","timestamp":"1795790432"},"entity":[
              {"id":"AVISO_528848","alert":{
                "activePeriod":[{"start":"1795778460"}],
                "informedEntity":[{"routeId":"10T0007C3"},{"routeId":"10T0011C4"}],
                "descriptionText":{"translation":[{"text":"#MadC3 #MadC4 Debido a una acumulación de trenes en Atocha.","language":"es"}]}}},
              {"id":"AVISO_528848","alert":{
                "informedEntity":[{"routeId":"10T0007C3"}],
                "descriptionText":{"translation":[{"text":"Repetido","language":"es"}]}}},
              {"id":"AVISO_528900","alert":{
                "informedEntity":[{"stopId":"18000"}],
                "descriptionText":{"translation":[{"text":"Ascensor fuera de servicio","language":"es"}]}}}
            ]}
            """;

    /** Comprueba que se leen las rutas, estaciones, inicio y descripción de cada aviso, sin repetir ids. */
    @Test
    void alertas_leeRutasInicioYDescripcionSinRepetirAvisos() {
        ClienteConServidor fixture = crearCliente();
        fixture.servidor.expect(requestTo(URL_INCIDENCIAS))
                .andRespond(withSuccess(FEED_ALERTAS, MediaType.APPLICATION_JSON));
        RedTransporte red = new RedTransporte();

        assertTrue(fixture.cliente.actualizarIncidencias(red));

        List<AvisoOficial> avisos = red.getAvisosOficiales();
        assertEquals(2, avisos.size());
        AvisoOficial atocha = avisos.get(0);
        assertEquals("AVISO_528848", atocha.id());
        assertEquals(Set.of("10T0007C3", "10T0011C4"), atocha.routeIds());
        assertEquals(1795778460L, atocha.inicioEpoch());
        assertNull(atocha.titulo());
        assertEquals("#MadC3 #MadC4 Debido a una acumulación de trenes en Atocha.", atocha.descripcion());

        AvisoOficial ascensor = avisos.get(1);
        assertTrue(ascensor.routeIds().isEmpty());
        assertEquals(Set.of("18000"), ascensor.stopIds());
        assertNull(ascensor.inicioEpoch());
        fixture.servidor.verify();
    }

    /** Comprueba que si la descarga de alertas falla se devuelve false y no se tocan los avisos anteriores. */
    @Test
    void alertas_errorHttp_devuelveFalseYConservaLosAvisosAnteriores() {
        ClienteConServidor fixture = crearCliente();
        fixture.servidor.expect(requestTo(URL_INCIDENCIAS))
                .andRespond(withSuccess(FEED_ALERTAS, MediaType.APPLICATION_JSON));
        fixture.servidor.expect(requestTo(URL_INCIDENCIAS)).andRespond(withServerError());
        RedTransporte red = new RedTransporte();

        assertTrue(fixture.cliente.actualizarIncidencias(red));
        assertFalse(fixture.cliente.actualizarIncidencias(red));

        assertEquals(2, red.getAvisosOficiales().size());
        fixture.servidor.verify();
    }

    /** Crea un cliente de Renfe conectado a un servidor HTTP simulado; lo usan todos los tests de esta clase. */
    private static ClienteConServidor crearCliente() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(restTemplate).build();
        ClienteApiRenfe cliente = new ClienteApiRenfe(restTemplate);
        ReflectionTestUtils.setField(cliente, "urlPosiciones", URL_POSICIONES);
        ReflectionTestUtils.setField(cliente, "urlIncidencias", URL_INCIDENCIAS);
        return new ClienteConServidor(cliente, servidor);
    }

    private record ClienteConServidor(ClienteApiRenfe cliente, MockRestServiceServer servidor) {}
}
