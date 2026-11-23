package com.techlink.service;

import java.time.LocalDate;
import java.time.LocalTime;

// Un estudiante pidió una sesión: queda pendiente hasta que el mentor la acepte o la rechace.
public record SesionSolicitadaEvent(Integer sesionId, Integer mentorUsuarioId, String nombreEstudiante, LocalDate fecha,
                                    LocalTime horaInicio, LocalTime horaFin) {
}
