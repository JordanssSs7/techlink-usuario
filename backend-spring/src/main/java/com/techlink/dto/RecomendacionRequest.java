package com.techlink.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

// HU-04: lo que el estudiante le pide al agente de IA: los cursos que quiere reforzar, su horario disponible
// y, si quiere, una nota con sus palabras sobre lo que le cuesta.
public record RecomendacionRequest(
        @NotNull(message = "Elige al menos un curso")
        @Size(min = 1, max = 5, message = "Elige entre 1 y 5 cursos")
        List<Integer> cursos,

        @NotNull(message = "Indica tu horario disponible")
        @Size(min = 1, max = 21, message = "Indica entre 1 y 21 horarios")
        @Valid
        List<Horario> horarios,

        @Size(max = 500, message = "La nota no puede superar los 500 caracteres")
        String nota
) {

    // Un bloque de tiempo libre del estudiante. diaSemana: 1 = lunes ... 7 = domingo.
    public record Horario(
            @NotNull(message = "Indica el día")
            @Min(value = 1, message = "El día debe ser de 1 (lunes) a 7 (domingo)")
            @Max(value = 7, message = "El día debe ser de 1 (lunes) a 7 (domingo)")
            Integer diaSemana,

            @NotNull(message = "Indica la hora de inicio")
            LocalTime horaInicio,

            @NotNull(message = "Indica la hora de fin")
            LocalTime horaFin
    ) {

        @AssertTrue(message = "La hora de fin debe ser posterior a la de inicio")
        public boolean isHorasValidas() {
            return horaInicio == null || horaFin == null || horaFin.isAfter(horaInicio);
        }
    }
}
