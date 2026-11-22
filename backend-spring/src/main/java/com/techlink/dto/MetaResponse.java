package com.techlink.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

// HU-11: una meta tal como la ve el estudiante o su mentor.
// propia: la puso el propio estudiante (sin mentor). atrasada: está pendiente y su fecha de entrega ya pasó.
// estudiante e idUsuarioEstudiante se llenan para el mentor, que ve las metas de varios alumnos.
public record MetaResponse(
        Integer id,
        String titulo,
        Integer idCurso,
        String curso,
        Integer idMentor,
        String mentor,
        String estudiante,
        Integer idUsuarioEstudiante,
        LocalDate fechaEntrega,
        boolean completada,
        LocalDateTime fechaCompletada,
        boolean atrasada,
        boolean propia
) {
}
