package servicio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import modelo.Ruta;

public class ServicioComparadorRutas {

    public List<Ruta> ordenarRutas(List<Ruta> rutas, Comparator<Ruta> criterio) {
        List<Ruta> ordenadas = new ArrayList<>(rutas);
        ordenadas.sort(criterio);
        return ordenadas;
    }

    public Ruta obtenerMejorRuta(List<Ruta> rutas, Comparator<Ruta> criterio) {
        if (rutas == null || rutas.isEmpty()) {
            return null;
        }
        return rutas.stream().min(criterio).orElse(null);
    }
}