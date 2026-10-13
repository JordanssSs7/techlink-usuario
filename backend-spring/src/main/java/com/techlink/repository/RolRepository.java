package com.techlink.repository;

import com.techlink.model.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Acceso a la tabla roles. Spring genera la consulta a partir del nombre del método.
public interface RolRepository extends JpaRepository<Rol, Integer> {
    Optional<Rol> findByNombre(String nombre);
}
