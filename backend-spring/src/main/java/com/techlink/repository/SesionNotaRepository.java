package com.techlink.repository;

import com.techlink.model.SesionNota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// HU-21: notas de las sesiones.
public interface SesionNotaRepository extends JpaRepository<SesionNota, Integer> {

    Optional<SesionNota> findBySesionId(Integer sesionId);

    @Query("SELECT n FROM SesionNota n WHERE n.sesion.id IN :ids")
    List<SesionNota> buscarPorSesiones(@Param("ids") Collection<Integer> ids);
}
