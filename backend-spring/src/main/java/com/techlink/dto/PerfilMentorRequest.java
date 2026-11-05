package com.techlink.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.List;

// HU-05: lo que el mentor guarda en PUT /api/mentor/perfil. Las listas reemplazan por completo a las anteriores.
// especialidad y biografia son opcionales: si no se envian, se conservan las que ya tenia.
public record PerfilMentorRequest(
        @Size(max = 150, message = "La especialidad no puede superar 150 caracteres")
        String especialidad,

        @Size(max = 2000, message = "La biografía no puede superar 2000 caracteres")
        String biografia,

        @NotNull(message = "Las fortalezas son obligatorias (puede ser una lista vacía)")
        @Size(max = 20, message = "Máximo 20 fortalezas")
        List<@NotBlank(message = "La fortaleza no puede estar vacía")
             @Size(min = 2, max = 100, message = "Cada fortaleza debe tener entre 2 y 100 caracteres") String> fortalezas,

        @NotNull(message = "Las áreas de conocimiento son obligatorias (puede ser una lista vacía)")
        @Size(max = 20, message = "Máximo 20 áreas")
        List<@NotBlank(message = "El área no puede estar vacía")
             @Size(min = 2, max = 100, message = "Cada área debe tener entre 2 y 100 caracteres") String> areas,

        @NotNull(message = "Los cursos son obligatorios (puede ser una lista vacía)")
        @Size(max = 30, message = "Máximo 30 cursos")
        List<@NotNull(message = "Id de curso inválido") Integer> cursoIds,

        @NotNull(message = "La disponibilidad es obligatoria (puede ser una lista vacía)")
        @Size(max = 50, message = "Máximo 50 horarios")
        List<@Valid Horario> disponibilidad
) {
    // Un horario semanal: dia 1 (lunes) a 7 (domingo). activo = false lo deja guardado pero sin considerarlo.
    public record Horario(
            @NotNull(message = "El día es obligatorio")
            @Min(value = 1, message = "El día debe estar entre 1 (lunes) y 7 (domingo)")
            @Max(value = 7, message = "El día debe estar entre 1 (lunes) y 7 (domingo)")
            Integer diaSemana,

            @NotNull(message = "La hora de inicio es obligatoria")
            LocalTime horaInicio,

            @NotNull(message = "La hora de fin es obligatoria")
            LocalTime horaFin,

            Boolean activo
    ) {
    }
}
