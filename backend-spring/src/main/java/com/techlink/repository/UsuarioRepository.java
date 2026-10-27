package com.techlink.repository;

import com.techlink.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Acceso a la tabla usuarios.
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    // HU-02: busca al usuario que intenta iniciar sesión.
    Optional<Usuario> findByCorreoInstitucional(String correoInstitucional);
}
