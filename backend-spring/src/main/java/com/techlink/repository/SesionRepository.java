
package com.techlink.repository;

import com.techlink.model.Estudiante;
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

    // HU-15: fecha y horas de las sesiones confirmadas de un mentor, para sumar las horas dictadas.
    @Query("SELECT s.fecha, s.horaInicio, s.horaFin FROM Sesion s WHERE s.mentor.id = :mentorId "
            + "AND s.estado = com.techlink.model.Sesion.EstadoSesion.CONFIRMADA")
    List<Object[]> horariosConfirmadosDeMentor(@Param("mentorId") Integer mentorId);

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

    // HU-23: sesiones confirmadas entre dos fechas (de todos los usuarios), para enviar los recordatorios.
    @Query("""
        SELECT s FROM Sesion s
        WHERE s.estado = com.techlink.model.Sesion.EstadoSesion.CONFIRMADA
          AND s.fecha BETWEEN :desde AND :hasta
        ORDER BY s.fecha, s.horaInicio
        """)
    List<Sesion> confirmadasEntre(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    // HU-26: sesiones confirmadas del usuario (como estudiante o como mentor) dentro de un rango de fechas.
    @Query("""
        SELECT s FROM Sesion s
        WHERE s.estado = com.techlink.model.Sesion.EstadoSesion.CONFIRMADA
          AND s.fecha BETWEEN :desde AND :hasta
          AND (s.estudiante.usuario.id = :usuarioId OR s.mentor.usuario.id = :usuarioId)
        ORDER BY s.fecha, s.horaInicio
        """)
    List<Sesion> confirmadasDelUsuarioEntre(@Param("usuarioId") Integer usuarioId,
                                            @Param("desde") LocalDate desde,
                                            @Param("hasta") LocalDate hasta);

    // HU-11: los estudiantes con los que un mentor tiene (o tuvo) sesiones confirmadas: a ellos puede ponerles metas.
    @Query("""
        SELECT DISTINCT s.estudiante FROM Sesion s
        WHERE s.mentor.usuario.id = :usuarioId AND s.estado = com.techlink.model.Sesion.EstadoSesion.CONFIRMADA
        """)
    List<Estudiante> estudiantesDeMentor(@Param("usuarioId") Integer usuarioId);
}
