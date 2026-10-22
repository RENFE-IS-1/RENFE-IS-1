package consultas;

import modelo.Estacion;
import repositorio.RedTransporte;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BuscadorEstaciones {
    private final RedTransporte red;

    public BuscadorEstaciones(RedTransporte red) {
        this.red = red;
    }

    public List<Estacion> buscar(String texto) {
        List<Estacion> resultado = new ArrayList<>();
        if (texto == null || texto.isBlank()) return resultado;

        // 1. Busqueda por ID exacto
        Estacion porId = red.getEstacion(texto.trim());
        if (porId != null) {
            resultado.add(porId);
            return resultado;
        }

        String buscado = normalizar(texto);

        // 2. Busqueda por nombre exacto (normalizado sin tildes ni mayúsculas)
        for (Estacion e : red.getAllEstaciones()) {
            if (normalizar(e.getNombre()).equals(buscado)) {
                resultado.add(e);
                return resultado; // Devuelve solo 1 si es coincidencia exacta
            }
        }

        // 3. Busqueda parcial (contiene la palabra)
        for (Estacion e : red.getAllEstaciones()) {
            if (normalizar(e.getNombre()).contains(buscado)) {
                resultado.add(e);
            }
        }

        // 4. Si no encontro nada por contención, busca coincidencias aproximadas (tolerancia a erratas)
        if (resultado.isEmpty() && buscado.length() >= 4) {
            for (Estacion e : red.getAllEstaciones()) {
                String norm = normalizar(e.getNombre());
                if (esSimilar(buscado, norm)) {
                    resultado.add(e);
                }
            }
        }

        resultado.sort(Comparator.comparing(Estacion::getNombre));
        return resultado;
    }

    public static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    private static boolean esSimilar(String s1, String s2) {
        // Algoritmo de distancia Levenshtein sencillo para tolerar pequeñas erratas
        int maxDist = Math.max(1, s1.length() / 4);
        int dist = 0;
        int minLen = Math.min(s1.length(), s2.length());
        for (int i = 0; i < minLen; i++) {
            if (s1.charAt(i) != s2.charAt(i)) dist++;
        }
        dist += Math.abs(s1.length() - s2.length());
        return dist <= maxDist;
    }
}