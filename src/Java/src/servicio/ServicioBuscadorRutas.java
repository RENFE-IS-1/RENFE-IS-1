package servicio;

import modelo.Estacion;
import modelo.Linea;
import modelo.Ruta;
import modelo.Tramo;
import repositorio.RedTransporte;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ServicioBuscadorRutas {
    private final RedTransporte red;

    public ServicioBuscadorRutas(RedTransporte red) {
        this.red = red;
    }

    public List<Ruta> buscarRutas(Estacion origen, Estacion destino) {
        List<Ruta> opciones = new ArrayList<>();
        if (origen.getId().equals(destino.getId())) return opciones;

        Set<Linea> lineasOrigen = red.getLineasEnEstacion(origen.getId());
        Set<Linea> lineasDestino = red.getLineasEnEstacion(destino.getId());
        
        // Base horaria simulada para el constructor de Tramo
        LocalTime horaBase = LocalTime.now();

        // 1. Opciones directas (0 transbordos)
        Set<Linea> directas = new HashSet<>(lineasOrigen);
        directas.retainAll(lineasDestino);

        if (!directas.isEmpty()) {
            Linea l = directas.iterator().next(); // Toma solo una línea representativa para evitar duplicados
            Ruta r = new Ruta();
            double dist = origen.distanciaEnKm(destino.getLatitud(), destino.getLongitud());
            long duracion = (long) Math.max(2, (dist * 1.33) + 2);
            double precio = calcularPrecio(dist);
            
            r.agregarTramo(new Tramo(origen, destino, l, horaBase, horaBase.plusMinutes(duracion), precio));
            opciones.add(r);
        }

        // 2. Opciones con 1 transbordo (limitado a 5 alternativas)
        int alternativasTransbordo = 0;
        for (Estacion intermedia : red.getAllEstaciones()) {
            if (intermedia.getId().equals(origen.getId()) || intermedia.getId().equals(destino.getId())) continue;
            
            Set<Linea> lineasInter = red.getLineasEnEstacion(intermedia.getId());
            Set<Linea> inter1 = new HashSet<>(lineasOrigen);
            inter1.retainAll(lineasInter);
            Set<Linea> inter2 = new HashSet<>(lineasDestino);
            inter2.retainAll(lineasInter);

            if (!inter1.isEmpty() && !inter2.isEmpty()) {
                Linea l1 = inter1.iterator().next();
                Linea l2 = inter2.iterator().next();
                
                if (l1.getId().equals(l2.getId())) continue;

                Ruta r = new Ruta();
                
                // Cálculo del primer Tramo
                double dist1 = origen.distanciaEnKm(intermedia.getLatitud(), intermedia.getLongitud());
                long duracion1 = (long) Math.max(2, (dist1 * 1.33) + 2);
                double precio1 = calcularPrecio(dist1);
                LocalTime llegadaIntermedia = horaBase.plusMinutes(duracion1);
                
                r.agregarTramo(new Tramo(origen, intermedia, l1, horaBase, llegadaIntermedia, precio1));
                
                // Cálculo del segundo Tramo (añadiendo 5 minutos fijos de transbordo)
                LocalTime salidaIntermedia = llegadaIntermedia.plusMinutes(5);
                double dist2 = intermedia.distanciaEnKm(destino.getLatitud(), destino.getLongitud());
                long duracion2 = (long) Math.max(2, (dist2 * 1.33) + 2);
                double precio2 = calcularPrecio(dist2);
                
                r.agregarTramo(new Tramo(intermedia, destino, l2, salidaIntermedia, salidaIntermedia.plusMinutes(duracion2), precio2));
                
                opciones.add(r);
                alternativasTransbordo++;
                if (alternativasTransbordo >= 5) break; 
            }
        }
        return opciones;
    }

    private double calcularPrecio(double distancia) {
        return Math.max(1.70, Math.min(1.70 + (distancia * 0.05), 5.50));
    }
}