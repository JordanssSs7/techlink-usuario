
package com.techlink.repository;

import com.techlink.model.Sesion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface SesionRepository extends JpaRepository<Sesion, Integer> {

    // HU-07: verificar que el mentor no tenga una sesión
    // confirmada que se superponga con el horario solicitado.
    @Query("""
        SELECT COUNT(s)
        FROM Sesion s
        WHERE s.mentor.id = :mentorId
          AND s.fecha = :fecha
          AND s.estado = com.techlink.model.Sesion.EstadoSesion.CONFIRMADA
          AND s.horaInicio < :horaFin
          AND s.horaFin > :horaInicio
        """)
    long contarSesionesSuperpuestas(
            @Param("mentorId") Integer mentorId,
            @Param("fecha") LocalDate fecha,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin
    );

    // HU-07: obtener únicamente las sesiones
    // pertenecientes al estudiante autenticado.
    List<Sesion> findByEstudianteUsuarioId(Integer usuarioId);

    // HU-19: igual que contarSesionesSuperpuestas, pero ignorando la propia sesión que se está reprogramando.
    @Query("""
        SELECT COUNT(s)
        FROM Sesion s
        WHERE s.mentor.id = :mentorId
          AND s.fecha = :fecha
          AND s.estado = com.techlink.model.Sesion.EstadoSesion.CONFIRMADA
          AND s.horaInicio < :horaFin
          AND s.horaFin > :horaInicio
          AND s.id <> :sesionId
        """)
    long contarSuperpuestasExcluyendo(
            @Param("mentorId") Integer mentorId,
            @Param("fecha") LocalDate fecha,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin,
            @Param("sesionId") Integer sesionId
    );

    // HU-20: historial del estudiante, de la más reciente a la más antigua.
    @Query("""
        SELECT s FROM Sesion s
        WHERE s.estudiante.usuario.id = :usuarioId
        ORDER BY s.fecha DESC, s.horaInicio DESC
        """)
    List<Sesion> historialDeEstudiante(@Param("usuarioId") Integer usuarioId);

    // HU-20: historial del mentor, de la más reciente a la más antigua.
    @Query("""
        SELECT s FROM Sesion s
        WHERE s.mentor.usuario.id = :usuarioId
        ORDER BY s.fecha DESC, s.horaInicio DESC
        """)
    List<Sesion> historialDeMentor(@Param("usuarioId") Integer usuarioId);
}
