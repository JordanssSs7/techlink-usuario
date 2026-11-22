package com.techlink.repository;

import com.techlink.model.SalaMensaje;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// HU-08: historial del chat de las salas virtuales.
public interface SalaMensajeRepository extends JpaRepository<SalaMensaje, Long> {

    // Los mensajes más recientes de una sala, del último al primero (el servicio los da vuelta para mostrarlos en orden).
    @Query("SELECT m FROM SalaMensaje m WHERE m.salaTipo = :tipo AND m.salaId = :salaId ORDER BY m.id DESC")
    List<SalaMensaje> recientes(@Param("tipo") String tipo, @Param("salaId") Integer salaId, Pageable pagina);
}
