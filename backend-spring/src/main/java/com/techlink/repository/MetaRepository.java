package com.techlink.repository;

import com.techlink.model.Meta;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

// HU-11 y HU-12: metas académicas.
public interface MetaRepository extends JpaRepository<Meta, Integer> {

    // Metas de un estudiante cuya fecha de entrega cae en un rango (una semana, por ejemplo), por fecha de entrega.
    @Query("""
        SELECT m FROM Meta m
        WHERE m.estudiante.usuario.id = :usuarioId AND m.fechaEntrega BETWEEN :desde AND :hasta
        ORDER BY m.fechaEntrega, m.id
        """)
    List<Meta> delEstudianteEntre(@Param("usuarioId") Integer usuarioId,
                                  @Param("desde") LocalDate desde,
                                  @Param("hasta") LocalDate hasta);

    // Metas que se quedaron sin cumplir antes de una fecha, de la más antigua a la más reciente.
    @Query("""
        SELECT m FROM Meta m
        WHERE m.estudiante.usuario.id = :usuarioId AND m.completada = false AND m.fechaEntrega < :antesDe
        ORDER BY m.fechaEntrega, m.id
        """)
    List<Meta> atrasadasDe(@Param("usuarioId") Integer usuarioId, @Param("antesDe") LocalDate antesDe);

    // Las metas pendientes con fecha de entrega después de una fecha (las de las próximas semanas), de la más cercana a la más lejana.
    @Query("""
        SELECT m FROM Meta m
        WHERE m.estudiante.usuario.id = :usuarioId AND m.completada = false AND m.fechaEntrega > :despuesDe
        ORDER BY m.fechaEntrega, m.id
        """)
    List<Meta> proximasDe(@Param("usuarioId") Integer usuarioId, @Param("despuesDe") LocalDate despuesDe, Pageable pagina);

    @Query("SELECT COUNT(m) FROM Meta m WHERE m.estudiante.usuario.id = :usuarioId AND m.completada = false")
    long contarPendientes(@Param("usuarioId") Integer usuarioId);

    // Metas que un mentor puso a sus estudiantes: primero las pendientes y, dentro de cada grupo, las más recientes.
    @Query("""
        SELECT m FROM Meta m
        WHERE m.mentor.usuario.id = :usuarioId
        ORDER BY m.completada, m.fechaEntrega DESC, m.id DESC
        """)
    List<Meta> asignadasPorMentor(@Param("usuarioId") Integer usuarioId);
}
