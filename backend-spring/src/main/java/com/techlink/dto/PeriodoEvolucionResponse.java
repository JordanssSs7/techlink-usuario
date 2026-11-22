package com.techlink.dto;

import java.time.LocalDate;

// HU-12: lo que pasó en una semana o un mes. autoevaluacion y nota son null si no registró ninguna en ese periodo.
public record PeriodoEvolucionResponse(
        String etiqueta,
        LocalDate desde,
        LocalDate hasta,
        double horas,
        int sesiones,
        Double autoevaluacion,
        Double nota,
        int metasTotal,
        int metasCompletadas
) {
}
