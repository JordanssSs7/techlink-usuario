package com.techlink.repository;

import com.techlink.model.UsuarioFoto;
import org.springframework.data.jpa.repository.JpaRepository;

// HU-13: fotos de perfil. El id de la foto es el id del usuario.
public interface UsuarioFotoRepository extends JpaRepository<UsuarioFoto, Integer> {
}
