package com.backendapi.repositorio;

import com.backendapi.modelo.DeclaracionIncidencia;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acceso a la tabla "declaraciones_incidencia" (declaraciones de los viajeros). */
public interface DeclaracionIncidenciaRepository extends JpaRepository<DeclaracionIncidencia, Long> {

    /**
     * Indica si un viajero ya declaró una incidencia.
     * Lo necesitamos para que un mismo viajero no cuente dos veces en la misma incidencia.
     */
    boolean existsByIncidenciaIdAndUsuarioId(Long incidenciaId, Long usuarioId);
}
