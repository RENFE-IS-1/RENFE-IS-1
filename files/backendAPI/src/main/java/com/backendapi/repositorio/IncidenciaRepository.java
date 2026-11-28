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
     * Busca la incidencia activa de un aviso oficial del feed de Renfe en una línea concreta.
     * Lo necesitamos para no registrar dos veces el mismo aviso, ya que el feed se descarga cada
     * 60 segundos (criterio 3). Se busca por aviso y línea porque un mismo aviso puede afectar a
     * varias líneas (p. ej. C3 y C4) y se guarda una incidencia por cada una.
     */
    Optional<Incidencia> findByIdExternoAndLineaAndFinIsNull(String idExterno, String linea);

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
     * Busca la incidencia activa de una línea con un tipo y un origen concretos.
     * Lo necesitamos para agrupar en una sola incidencia las declaraciones de varios viajeros sobre
     * la misma línea y el mismo tipo.
     */
    Optional<Incidencia> findByLineaAndTipoAndOrigenAndFinIsNull(String linea, String tipo, OrigenIncidencia origen);

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
