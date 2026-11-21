package com.techlink.dto;

import java.time.LocalDate;
import java.time.LocalTime;

// HU-26: un evento del calendario. tipo = SESION (mentoría agendada) o CHARLA (charla a la que se inscribió).
// detalle: la otra persona de la sesión, o la temática de la charla.
public record EventoCalendarioResponse(
        String tipo,
        Integer id,
        String titulo,
        String detalle,
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin,
        String enlace
) {
}
