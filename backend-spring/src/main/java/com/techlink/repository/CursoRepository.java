package com.techlink.repository;

import com.techlink.model.Curso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// HU-05: acceso al catalogo de cursos.
public interface CursoRepository extends JpaRepository<Curso, Integer> {

    // Cursos que se pueden elegir (los inactivos no se ofrecen), en orden alfabetico.
    List<Curso> findByActivoTrueOrderByNombreAsc();
}
