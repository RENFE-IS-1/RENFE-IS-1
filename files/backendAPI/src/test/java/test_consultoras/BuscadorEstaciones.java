package test_consultoras;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import consultas.BuscadorEstaciones;
import modelo.Estacion;
import repositorio.RedTransporte;

class BuscadorEstacionesTest {

    @Test
    void encuentraEstacionSinDistinguirMayusculas() {
        RedTransporte red = new RedTransporte();
        red.addEstacion(new Estacion("1", "Atocha", 40.0, -3.0));
        BuscadorEstaciones buscador = new BuscadorEstaciones(red);

        List<Estacion> resultado = buscador.buscar("ATOCHA");

        assertEquals(1, resultado.size());
        assertEquals("Atocha", resultado.get(0).getNombre());
    }

    @Test
    void encuentraEstacionSinDistinguirTildes() {
        RedTransporte red = new RedTransporte();
        red.addEstacion(new Estacion("2", "Príncipe Pío", 40.0, -3.0));
        BuscadorEstaciones buscador = new BuscadorEstaciones(red);

        List<Estacion> resultado = buscador.buscar("principe pio");

        assertEquals(1, resultado.size());
        assertEquals("Príncipe Pío", resultado.get(0).getNombre());
    }

    @Test
    void devuelveListaVaciaCuandoElTextoEstaVacio() {
        RedTransporte red = new RedTransporte();
        red.addEstacion(new Estacion("1", "Atocha", 40.0, -3.0));
        BuscadorEstaciones buscador = new BuscadorEstaciones(red);

        List<Estacion> resultado = buscador.buscar("   ");

        assertTrue(resultado.isEmpty());
    }

    @Test
    void devuelveListaVaciaCuandoNoHayCoincidencias() {
        RedTransporte red = new RedTransporte();
        red.addEstacion(new Estacion("1", "Atocha", 40.0, -3.0));
        BuscadorEstaciones buscador = new BuscadorEstaciones(red);

        List<Estacion> resultado = buscador.buscar("Barcelona");

        assertTrue(resultado.isEmpty());
    }
}
