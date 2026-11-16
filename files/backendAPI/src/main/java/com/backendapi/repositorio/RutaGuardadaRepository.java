package com.backendapi.repositorio;

import com.backendapi.modelo.RutaGuardada;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RutaGuardadaRepository extends JpaRepository<RutaGuardada, Long> {
    List<RutaGuardada> findByUsuarioId(Long usuarioId);
    Optional<RutaGuardada> findByIdAndUsuarioId(Long id, Long usuarioId);
}