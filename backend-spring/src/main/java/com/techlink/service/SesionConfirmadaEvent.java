
package com.techlink.service;

import java.time.LocalDate;
import java.time.LocalTime;

public record SesionConfirmadaEvent(
        Integer sesionId,
        String correoEstudiante,
        String correoMentor,
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin,
        String enlaceReunion
) {
}
