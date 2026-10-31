package test_consultoras;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import consultas.BuscadorEstaciones;
import modelo.Estacion;
import repositorio.RedTransporte;

/** Pruebas pequeñas de la búsqueda de estaciones. */
class BuscadorEstacionesTest {

    /**
     * PRIMER TEST: comprueba que escribir el nombre en mayúsculas
     * sigue encontrando una estación cuyo nombre está escrito normalmente.
     */
    @Test
    void encuentraEstacionSinDistinguirMayusculas() {
        // Preparar una red de prueba vacía; no cargamos los horarios reales.
        RedTransporte red = new RedTransporte();
        // Crear y añadir una estación conocida para este escenario.
        red.addEstacion(new Estacion("1", "Atocha", 40.0, -3.0));
        // Crear el objeto que vamos a probar y darle la red con la estación.
        BuscadorEstaciones buscador = new BuscadorEstaciones(red);

        // Ejecutar la búsqueda y guardar los resultados que devuelve.
        List<Estacion> resultado = buscador.buscar("ATOCHA");

        // ASSERT: comprobar que se esperaba una coincidencia y llegó una.
        assertEquals(1, resultado.size());
        // ASSERT: comprobar que la estación encontrada es Atocha.
        assertEquals("Atocha", resultado.get(0).getNombre());
    }

    /**
     * SEGUNDO TEST: comprueba que se ignoran las tildes al buscar.
     * "principe pio" debe encontrar "Príncipe Pío".
     */
    @Test
    void encuentraEstacionSinDistinguirTildes() {
        // Preparar una red de prueba con una estación que contiene tildes.
        RedTransporte red = new RedTransporte();
        red.addEstacion(new Estacion("2", "Príncipe Pío", 40.0, -3.0));
        // Crear el buscador asociado a esa red.
        BuscadorEstaciones buscador = new BuscadorEstaciones(red);

        // Ejecutar la búsqueda sin escribir las tildes.
        List<Estacion> resultado = buscador.buscar("principe pio");

        // ASSERT: debe haber exactamente una coincidencia.
        assertEquals(1, resultado.size());
        // ASSERT: su nombre original conserva las tildes.
        assertEquals("Príncipe Pío", resultado.get(0).getNombre());
    }

    /** Comprueba que un identificador de estación exacto también sirve para buscar. */
    @Test
    void encuentraEstacionPorId() {
        RedTransporte red = new RedTransporte();
        red.addEstacion(new Estacion("1", "Atocha", 40.0, -3.0));
        BuscadorEstaciones buscador = new BuscadorEstaciones(red);

        List<Estacion> resultado = buscador.buscar("1");

        assertEquals(1, resultado.size());
        assertEquals("Atocha", resultado.get(0).getNombre());
    }

    /** Comprueba que una búsqueda parcial devuelve las coincidencias en orden alfabético. */
    @Test
    void devuelveCoincidenciasParcialesOrdenadasAlfabeticamente() {
        RedTransporte red = new RedTransporte();
        red.addEstacion(new Estacion("3", "San Sebastián", 40.0, -3.0));
        red.addEstacion(new Estacion("4", "San Blas", 40.0, -3.0));
        red.addEstacion(new Estacion("5", "San Fernando", 40.0, -3.0));
        BuscadorEstaciones buscador = new BuscadorEstaciones(red);

        List<Estacion> resultado = buscador.buscar("San");

        assertEquals(3, resultado.size());
        assertEquals("San Blas", resultado.get(0).getNombre());
        assertEquals("San Fernando", resultado.get(1).getNombre());
        assertEquals("San Sebastián", resultado.get(2).getNombre());
    }

    /** Comprueba que una errata de una letra puede producir una coincidencia aproximada. */
    @Test
    void encuentraEstacionConUnaErrataPequena() {
        RedTransporte red = new RedTransporte();
        red.addEstacion(new Estacion("1", "Atocha", 40.0, -3.0));
        BuscadorEstaciones buscador = new BuscadorEstaciones(red);

        List<Estacion> resultado = buscador.buscar("Atosha");

        assertEquals(1, resultado.size());
        assertEquals("Atocha", resultado.get(0).getNombre());
    }

    /** Comprueba que un texto formado solo por espacios no devuelve resultados. */
    @Test
    void devuelveListaVaciaCuandoElTextoEstaEnBlanco() {
        RedTransporte red = new RedTransporte();
        red.addEstacion(new Estacion("1", "Atocha", 40.0, -3.0));
        BuscadorEstaciones buscador = new BuscadorEstaciones(red);

        List<Estacion> resultado = buscador.buscar("   ");

        assertTrue(resultado.isEmpty());
    }

    /** Comprueba que el buscador acepta null y responde con una lista vacía. */
    @Test
    void devuelveListaVaciaCuandoElTextoEsNulo() {
        RedTransporte red = new RedTransporte();
        BuscadorEstaciones buscador = new BuscadorEstaciones(red);

        List<Estacion> resultado = buscador.buscar(null);

        assertTrue(resultado.isEmpty());
    }

    /** Comprueba el caso en que la red no contiene ninguna coincidencia. */
    @Test
    void devuelveListaVaciaCuandoNoHayCoincidencias() {
        RedTransporte red = new RedTransporte();
        red.addEstacion(new Estacion("1", "Atocha", 40.0, -3.0));
        BuscadorEstaciones buscador = new BuscadorEstaciones(red);

        List<Estacion> resultado = buscador.buscar("Barcelona");

        assertTrue(resultado.isEmpty());
    }

    /** Comprueba que los espacios alrededor del texto de búsqueda no afectan al resultado. */
    @Test
    void ignoraEspaciosAlrededorDelNombre() {
        RedTransporte red = new RedTransporte();
        red.addEstacion(new Estacion("1", "Atocha", 40.0, -3.0));
        BuscadorEstaciones buscador = new BuscadorEstaciones(red);

        List<Estacion> resultado = buscador.buscar("  Atocha  ");

        assertEquals(1, resultado.size());
        assertEquals("Atocha", resultado.get(0).getNombre());
    }

    /** Comprueba la función auxiliar que normaliza tildes, mayúsculas y espacios. */
    @Test
    void normalizaMayusculasTildesYEspacios() {
        assertEquals("principe pio", BuscadorEstaciones.normalizar("  Príncipe Pío  "));
    }

    /** Comprueba que normalizar null devuelve texto vacío. */
    @Test
    void normalizaTextoNuloComoCadenaVacia() {
        assertEquals("", BuscadorEstaciones.normalizar(null));
    }
}
