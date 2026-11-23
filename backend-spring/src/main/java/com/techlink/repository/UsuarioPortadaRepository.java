package com.techlink.repository;

import com.techlink.model.UsuarioPortada;
import org.springframework.data.jpa.repository.JpaRepository;

// Portadas de perfil. El id de la portada es el id del usuario.
public interface UsuarioPortadaRepository extends JpaRepository<UsuarioPortada, Integer> {
}
