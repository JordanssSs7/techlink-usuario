package com.techlink.repository;

import com.techlink.model.Estudiante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Acceso a la tabla estudiantes.
public interface EstudianteRepository extends JpaRepository<Estudiante, Integer> {
    boolean existsByCodigoEstudiante(String codigoEstudiante);

    // HU-02: perfil de estudiante de un usuario (para leer o cambiar su ciclo).
    Optional<Estudiante> findByUsuarioId(Integer idUsuario);
}
