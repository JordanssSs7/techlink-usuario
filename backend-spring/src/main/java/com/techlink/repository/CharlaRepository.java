package com.techlink.repository;

import com.techlink.model.Charla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// HU-06, HU-25 y HU-26: consultas de charlas virtuales (solo lectura).
public interface CharlaRepository extends JpaRepository<Charla, Integer> {

    // HU-06: charlas activas que todavía no terminaron, de la más próxima a la más lejana.
    @Query("""
        SELECT c FROM Charla c
        WHERE c.activo = true
          AND (c.fecha > :hoy OR (c.fecha = :hoy AND c.horaFin > :ahora))
        ORDER BY c.fecha, c.horaInicio, c.id
        """)
    List<Charla> vigentes(@Param("hoy") LocalDate hoy, @Param("ahora") LocalTime ahora);

    // HU-25: las charlas a las que se inscribió el usuario (también las desactivadas, que se marcan como tales),
    // de la más reciente a la más antigua. El servicio las reordena para mostrar primero las próximas.
    @Query("""
        SELECT c FROM Charla c
        WHERE c.id IN (SELECT i.idCharla FROM CharlaInscripcion i WHERE i.idUsuario = :usuarioId)
        ORDER BY c.fecha DESC, c.horaInicio DESC
        """)
    List<Charla> inscritasDe(@Param("usuarioId") Integer usuarioId);

    // HU-26: charlas activas del calendario del usuario dentro de un rango de fechas.
    @Query("""
        SELECT c FROM Charla c
        WHERE c.activo = true
          AND c.fecha BETWEEN :desde AND :hasta
          AND c.id IN (SELECT i.idCharla FROM CharlaInscripcion i WHERE i.idUsuario = :usuarioId)
        ORDER BY c.fecha, c.horaInicio
        """)
    List<Charla> inscritasEntre(@Param("usuarioId") Integer usuarioId,
                                @Param("desde") LocalDate desde,
                                @Param("hasta") LocalDate hasta);
}
