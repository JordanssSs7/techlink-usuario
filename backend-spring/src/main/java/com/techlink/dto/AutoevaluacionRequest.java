package com.techlink.dto;

import java.math.BigDecimal;

// HU-12: lo que manda el estudiante al autoevaluarse: de 1 a 5, un curso (opcional), una nota de 0 a 20 (opcional) y un comentario.
public record AutoevaluacionRequest(Integer idCurso, Integer puntaje, BigDecimal nota, String comentario) {
}
