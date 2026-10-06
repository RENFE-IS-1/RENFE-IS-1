package consultas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.Test;

import loader.GTFSLoader;
import modelo.Estacion;
import repositorio.RedTransporte;

public class BuscadorEstacionesTest {
    private static BuscadorEstaciones buscador;

    @BeforeClass
    public static void cargarDatos() {
        RedTransporte red = new RedTransporte();
        new GTFSLoader().cargarDesdeDirectorio("test/recursos/gtfs_prueba/", red);
        buscador = new BuscadorEstaciones(red);
    }

    private static List<String> ids(List<Estacion> estaciones) {
        return estaciones.stream().map(Estacion::getId).collect(Collectors.toList());
    }

    @Test
    public void buscaPorId() {
        assertEquals(List.of("2"), ids(buscador.buscar("2")));
    }

    @Test
    public void ignoraMayusculasYTildes() {
        assertEquals(List.of("1"), ids(buscador.buscar("estacion arbol")));
    }

    @Test
    public void unNombreExactoTienePrioridadSobreLosParciales() {
        // "Bosque" también está contenido en "Bosquecillo", pero "Estación Bosque" coincide exactamente
        assertEquals(List.of("2"), ids(buscador.buscar("Estación Bosque")));
    }

    @Test
    public void devuelveVariasSiElFragmentoEsAmbiguo() {
        assertEquals(List.of("4", "2"), ids(buscador.buscar("bosque"))); // ordenadas por nombre
    }

    @Test
    public void devuelveListaVaciaSiNoHayCoincidencias() {
        assertTrue(buscador.buscar("xyz").isEmpty());
        assertTrue(buscador.buscar("  ").isEmpty());
    }
}
