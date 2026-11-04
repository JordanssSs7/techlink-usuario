package com.techlink.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// HU-02 (Google): el ciclo que elige el estudiante en su primer ingreso (PATCH /api/estudiantes/me).
public record CicloRequest(
        @NotNull(message = "El ciclo es obligatorio")
        @Min(value = 1, message = "El ciclo debe estar entre 1 y 6")
        @Max(value = 6, message = "El ciclo debe estar entre 1 y 6")
        Integer ciclo
) {
}
