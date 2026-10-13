package com.techlink.repository;

import com.techlink.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

// Acceso a la tabla usuarios.
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    // Sirve para detectar correos ya registrados.
    boolean existsByCorreoInstitucional(String correoInstitucional);
}
