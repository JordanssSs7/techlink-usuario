package com.techlink.repository;

import com.techlink.model.CharlaInscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// HU-25: inscripciones a charlas.
public interface CharlaInscripcionRepository extends JpaRepository<CharlaInscripcion, Integer> {

    boolean existsByIdCharlaAndIdUsuario(Integer idCharla, Integer idUsuario);

    Optional<CharlaInscripcion> findByIdCharlaAndIdUsuario(Integer idCharla, Integer idUsuario);

    // Ids de las charlas a las que está inscrito el usuario (para marcar "Inscrito" en las listas).
    @Query("SELECT i.idCharla FROM CharlaInscripcion i WHERE i.idUsuario = :usuarioId")
    List<Integer> idsDeCharlas(@Param("usuarioId") Integer usuarioId);
}
