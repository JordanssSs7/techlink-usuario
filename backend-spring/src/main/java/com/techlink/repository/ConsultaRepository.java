package com.techlink.repository;

import com.techlink.model.Consulta;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// HU-17: consultas del foro. Las ocultas por el administrador (HU-54) nunca se devuelven.
public interface ConsultaRepository extends JpaRepository<Consulta, Integer> {

    // Las más recientes primero; se puede filtrar por curso y por una palabra (ya en minúsculas y entre %).
    @Query("""
            SELECT c FROM Consulta c
            WHERE c.oculto = false
              AND (:cursoId IS NULL OR c.curso.id = :cursoId)
              AND (:texto IS NULL OR LOWER(c.titulo) LIKE :texto OR LOWER(c.contenido) LIKE :texto)
            ORDER BY c.fechaCreacion DESC, c.id DESC
            """)
    List<Consulta> buscar(@Param("cursoId") Integer cursoId, @Param("texto") String texto, Pageable pagina);

    Optional<Consulta> findByIdAndOcultoFalse(Integer id);
}
