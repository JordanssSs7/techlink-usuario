package com.techlink.dto;

import java.math.BigDecimal;

// HU-09: la calificación guardada y la reputación que quedó el mentor después de recalcularla.
public record CalificacionResponse(
        Integer estrellas,
        String comentario,
        BigDecimal reputacionMentor,
        Integer totalCalificacionesMentor
) {
}
