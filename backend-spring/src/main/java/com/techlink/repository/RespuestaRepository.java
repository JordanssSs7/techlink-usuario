package com.techlink.repository;

import com.techlink.model.Respuesta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

// HU-17: respuestas del foro. La marcada como útil va primero y después las más antiguas.
public interface RespuestaRepository extends JpaRepository<Respuesta, Integer> {

    List<Respuesta> findByConsultaIdAndOcultoFalseOrderByUtilDescFechaCreacionAscIdAsc(Integer idConsulta);

    List<Respuesta> findByConsultaId(Integer idConsulta);

    // Por consulta: cuántas respuestas visibles tiene y cuántas están marcadas como útiles.
    @Query("""
            SELECT r.consulta.id, COUNT(r), SUM(CASE WHEN r.util = true THEN 1 ELSE 0 END)
            FROM Respuesta r
            WHERE r.consulta.id IN :ids AND r.oculto = false
            GROUP BY r.consulta.id
            """)
    List<Object[]> resumenPorConsultas(@Param("ids") Collection<Integer> ids);
}
