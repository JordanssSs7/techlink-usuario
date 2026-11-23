package com.techlink.repository;

import com.techlink.model.ForoGuardado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Consultas del foro guardadas por cada persona.
public interface ForoGuardadoRepository extends JpaRepository<ForoGuardado, Integer> {

    Optional<ForoGuardado> findByIdUsuarioAndIdConsulta(Integer idUsuario, Integer idConsulta);

    List<ForoGuardado> findByIdUsuarioOrderByIdDesc(Integer idUsuario);
}
