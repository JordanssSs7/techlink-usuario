package com.techlink.repository;

import com.techlink.model.Autoevaluacion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

// HU-12: autoevaluaciones del estudiante.
public interface AutoevaluacionRepository extends JpaRepository<Autoevaluacion, Integer> {

    @Query("""
        SELECT a FROM Autoevaluacion a
        WHERE a.estudiante.usuario.id = :usuarioId AND a.fecha BETWEEN :desde AND :hasta
        ORDER BY a.fecha, a.id
        """)
    List<Autoevaluacion> delEstudianteEntre(@Param("usuarioId") Integer usuarioId,
                                            @Param("desde") LocalDate desde,
                                            @Param("hasta") LocalDate hasta);

    @Query("""
        SELECT a FROM Autoevaluacion a
        WHERE a.estudiante.usuario.id = :usuarioId
        ORDER BY a.fecha DESC, a.id DESC
        """)
    List<Autoevaluacion> recientes(@Param("usuarioId") Integer usuarioId, Pageable pagina);

    // Cuántas registró ya en un día (se limita para que no se llene la gráfica de un solo día).
    @Query("SELECT COUNT(a) FROM Autoevaluacion a WHERE a.estudiante.usuario.id = :usuarioId AND a.fecha = :fecha")
    long contarDelDia(@Param("usuarioId") Integer usuarioId, @Param("fecha") LocalDate fecha);
}
