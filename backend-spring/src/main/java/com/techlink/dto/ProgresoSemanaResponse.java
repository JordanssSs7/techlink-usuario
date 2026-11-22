package com.techlink.dto;

import java.time.LocalDate;

// HU-11: cuántas metas de una semana (de lunes a domingo) lleva cumplidas el estudiante. porcentaje va de 0 a 100.
public record ProgresoSemanaResponse(
        LocalDate desde,
        LocalDate hasta,
        int total,
        int completadas,
        int porcentaje
) {
}
