package com.backendapi.repositorio;

import com.backendapi.modelo.EstacionFavorita;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EstacionFavoritaRepository extends JpaRepository<EstacionFavorita, Long> {
    List<EstacionFavorita> findByUsuarioId(Long usuarioId);
    Optional<EstacionFavorita> findByUsuarioIdAndStopId(Long usuarioId, String stopId);
}