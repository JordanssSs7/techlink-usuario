package com.techlink.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// HU-12: una autoevaluación registrada.
public record AutoevaluacionResponse(
        Integer id,
        Integer idCurso,
        String curso,
        Integer puntaje,
        BigDecimal nota,
        String comentario,
        LocalDate fecha
) {
}
