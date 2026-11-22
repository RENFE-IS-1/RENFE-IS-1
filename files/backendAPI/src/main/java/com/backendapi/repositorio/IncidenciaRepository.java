package com.backendapi.repositorio;

import com.backendapi.modelo.Incidencia;
import com.backendapi.modelo.OrigenIncidencia;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a la tabla "incidencias" (US-017).
 *
 * Spring Data genera la consulta de cada método a partir de su nombre. "FinIsNull" significa
 * "sin hora de fin", es decir, incidencias que siguen activas.
 * Los métodos para guardar (save) y leer (findById, findAll) se heredan de JpaRepository.
 */
public interface IncidenciaRepository extends JpaRepository<Incidencia, Long> {

    /**
     * Busca el aviso oficial activo con ese identificador del feed de Renfe.
     * Lo necesitamos para no registrar dos veces el mismo aviso, ya que el feed se descarga
     * cada 60 segundos (criterio 3).
     */
    Optional<Incidencia> findByIdExternoAndFinIsNull(String idExterno);

    /**
     * Busca la incidencia activa de una línea con un origen concreto.
     * Lo necesitamos para no crear otra incidencia por retraso en una línea mientras se siga
     * detectando la que ya existe (criterio 3).
     */
    Optional<Incidencia> findByLineaAndOrigenAndFinIsNull(String linea, OrigenIncidencia origen);

    /**
     * Devuelve todas las incidencias activas de un origen.
     * Lo necesitamos para finalizar, con su hora de fin, las que ya no aparecen en la última
     * actualización (criterio 4).
     */
    List<Incidencia> findByOrigenAndFinIsNull(OrigenIncidencia origen);

    /**
     * Devuelve todas las incidencias activas, de la que empezó más tarde a la más antigua.
     * Lo necesitamos para el endpoint de incidencias y para mostrar en US-031 las incidencias
     * activas en este momento.
     */
    List<Incidencia> findByFinIsNullOrderByInicioDesc();

    /**
     * Devuelve todas las incidencias de una línea, activas y finalizadas, de la más reciente a la
     * más antigua. Lo necesitamos para que US-031 muestre el histórico de una línea y distinga las
     * incidencias activas de las finalizadas.
     */
    List<Incidencia> findByLineaOrderByInicioDesc(String linea);
}
