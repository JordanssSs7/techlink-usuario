package com.techlink.dto;

import java.time.LocalDate;
import java.time.LocalTime;

// HU-19: el nuevo horario al que el estudiante quiere mover su sesión.
public record ReprogramarRequest(
        Integer disponibilidadId,
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin
) {
}
