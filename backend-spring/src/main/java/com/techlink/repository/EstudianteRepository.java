package com.techlink.repository;

import com.techlink.model.Estudiante;
import org.springframework.data.jpa.repository.JpaRepository;

// Acceso a la tabla estudiantes.
public interface EstudianteRepository extends JpaRepository<Estudiante, Integer> {
    boolean existsByCodigoEstudiante(String codigoEstudiante);
}
