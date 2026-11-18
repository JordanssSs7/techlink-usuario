package com.techlink.repository;

import com.techlink.model.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// HU-09: calificaciones de las sesiones.
public interface CalificacionRepository extends JpaRepository<Calificacion, Integer> {

    // Promedio de estrellas y cantidad de calificaciones que recibió un mentor.
    interface Resumen {
        Double getPromedio();

        Long getTotal();
    }

    Optional<Calificacion> findBySesionId(Integer sesionId);

    @Query("SELECT c FROM Calificacion c WHERE c.sesion.id IN :ids")
    List<Calificacion> buscarPorSesiones(@Param("ids") Collection<Integer> ids);

    // HU-14: las calificaciones con comentario más recientes de un mentor.
    @Query("SELECT c FROM Calificacion c WHERE c.sesion.mentor.id = :mentorId AND c.comentario IS NOT NULL "
            + "ORDER BY c.fechaCreacion DESC, c.id DESC")
    List<Calificacion> ultimosComentarios(@Param("mentorId") Integer mentorId, Pageable pagina);

    @Query("SELECT AVG(c.estrellas) AS promedio, COUNT(c) AS total FROM Calificacion c WHERE c.sesion.mentor.id = :mentorId")
    Resumen resumenDeMentor(@Param("mentorId") Integer mentorId);
}
