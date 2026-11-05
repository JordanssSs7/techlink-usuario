package com.techlink.dto;

import java.time.LocalTime;
import java.util.List;

// HU-05: el perfil del mentor tal como se guarda. Incluye todos sus horarios, activos o no, para poder editarlos.
public record PerfilMentorResponse(
        String especialidad,
        String biografia,
        List<String> fortalezas,
        List<String> areas,
        List<CursoResponse> cursos,
        List<HorarioResponse> disponibilidad
) {
    public record HorarioResponse(
            Integer id,
            Integer diaSemana,
            LocalTime horaInicio,
            LocalTime horaFin,
            Boolean activo
    ) {
    }
}
