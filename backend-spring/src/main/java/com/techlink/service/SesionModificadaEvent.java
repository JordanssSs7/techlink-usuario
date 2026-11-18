package com.techlink.service;

import java.time.LocalDate;
import java.time.LocalTime;

// HU-19: una sesión se canceló o se reprogramó. Se usa para avisar por correo después de guardar el cambio.
public record SesionModificadaEvent(
        Tipo tipo,
        Integer sesionId,
        String correoEstudiante,
        String correoMentor,
        LocalDate fechaAnterior,
        LocalTime horaInicioAnterior,
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin,
        String enlaceReunion
) {

    public enum Tipo {
        CANCELADA,
        REPROGRAMADA
    }
}
