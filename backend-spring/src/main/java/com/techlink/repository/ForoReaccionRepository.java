package com.techlink.repository;

import com.techlink.model.ForoReaccion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// Reacciones del foro a consultas y respuestas.
public interface ForoReaccionRepository extends JpaRepository<ForoReaccion, Integer> {

    Optional<ForoReaccion> findByIdUsuarioAndObjetoAndIdObjeto(Integer idUsuario, String objeto, Integer idObjeto);

    List<ForoReaccion> findByObjetoAndIdObjetoIn(String objeto, Collection<Integer> ids);
}
