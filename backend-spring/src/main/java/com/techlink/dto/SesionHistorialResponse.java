package com.techlink.dto;

import java.time.LocalDate;
import java.time.LocalTime;

// HU-20: una sesión del historial, con todo lo que la pantalla necesita para mostrar sus acciones.
// estado: PROGRAMADA (confirmada y aún no termina), COMPLETADA (confirmada y ya terminó) o CANCELADA.
// contraparte: el mentor si quien consulta es estudiante, o el estudiante si quien consulta es mentor.
public record SesionHistorialResponse(
        Integer id,
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin,
        String estado,
        Integer mentorId,
        String curso,
        String contraparte,
        String enlaceReunion,
        Integer estrellas,
        String comentario,
        String nota,
        boolean puedeCalificar,
        boolean puedeModificar,
        boolean puedeEditarNota
) {
}
