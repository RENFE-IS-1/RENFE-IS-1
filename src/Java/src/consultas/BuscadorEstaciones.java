package consultas;

import modelo.Estacion;
import repositorio.RedTransporte;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Búsqueda de estaciones por ID o por nombre (sin distinguir mayúsculas ni tildes).
 * Misma lógica que la búsqueda de la terminal en Main, extraída para poder reutilizarla
 * desde otras consultas (US-004 y US-001).
 */
public class BuscadorEstaciones {
    private final RedTransporte red;

    public BuscadorEstaciones(RedTransporte red) {
        this.red = red;
    }

    /**
     * Devuelve las estaciones que corresponden al texto: una sola si hay coincidencia por ID,
     * por nombre exacto o por un único nombre parcial; varias si el fragmento es ambiguo;
     * ninguna si no hay coincidencias.
     */
    public List<Estacion> buscar(String texto) {
        List<Estacion> resultado = new ArrayList<>();
        if (texto == null || texto.isBlank()) return resultado;

        Estacion porId = red.getEstacion(texto.trim());
        if (porId != null) {
            resultado.add(porId);
            return resultado;
        }

        String buscado = normalizar(texto);
        for (Estacion e : red.getAllEstaciones()) {
            if (normalizar(e.getNombre()).equals(buscado)) {
                resultado.add(e);
                return resultado;
            }
        }

        for (Estacion e : red.getAllEstaciones()) {
            if (normalizar(e.getNombre()).contains(buscado)) resultado.add(e);
        }
        resultado.sort(Comparator.comparing(Estacion::getNombre));
        return resultado;
    }

    public static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
