package com.techlink.dto;

import java.util.List;

// HU-04: los mentores que el agente recomienda, del mas compatible al menos compatible (maximo 3).
// generadoConIa dice la verdad sobre el origen: true si la IA hizo la recomendacion, false si se uso el calculo base.
public record RecomendacionResponse(
        boolean generadoConIa,
        String mensaje,
        List<MentorRecomendado> mentores
) {

    public record MentorRecomendado(
            Integer idMentor,
            String nombres,
            String apellidos,
            String especialidad,
            int porcentaje,
            List<String> cursosEnComun,
            List<String> fortalezas,
            List<String> areas,
            List<HorarioEnComun> horariosEnComun,
            String razon
    ) {
    }

    // Una franja en la que el estudiante y el mentor coinciden (horas en formato HH:mm).
    public record HorarioEnComun(Integer diaSemana, String horaInicio, String horaFin) {
    }
}
