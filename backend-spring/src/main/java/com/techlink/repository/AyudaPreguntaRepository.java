package com.techlink.repository;

import com.techlink.model.AyudaPregunta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

// HU-30: preguntas frecuentes del centro de ayuda.
public interface AyudaPreguntaRepository extends JpaRepository<AyudaPregunta, Integer> {

    // Las activas con su tema, ordenadas por tema y por posición dentro del tema.
    @Query("SELECT p FROM AyudaPregunta p JOIN FETCH p.tema t WHERE p.activa = true ORDER BY t.orden, t.id, p.orden, p.id")
    List<AyudaPregunta> activas();
}
