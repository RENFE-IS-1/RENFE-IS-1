package servicio;

import java.util.Comparator;
import modelo.Ruta;

public class ComparadoresRuta {

    public static final Comparator<Ruta> POR_TIEMPO = 
        Comparator.comparingLong(Ruta::getTiempoTotalMinutos);

    public static final Comparator<Ruta> POR_PRECIO = 
        Comparator.comparingDouble(Ruta::getPrecioTotal);

    public static final Comparator<Ruta> POR_TRANSBORDOS = 
        Comparator.comparingInt(Ruta::getNumeroTransbordos);
}