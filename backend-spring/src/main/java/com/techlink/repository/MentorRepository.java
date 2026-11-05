package com.techlink.repository;

import com.techlink.model.Mentor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// HU-05: acceso a la tabla mentores.
public interface MentorRepository extends JpaRepository<Mentor, Integer> {

    // Perfil de mentor de un usuario (todavia no existe hasta que guarda su perfil por primera vez).
    Optional<Mentor> findByUsuarioId(Integer idUsuario);
}
