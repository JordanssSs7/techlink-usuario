package com.techlink.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

// HU-04: los cursos criticos que el estudiante elige del catalogo (ids de GET /api/cursos). Lista vacia = quitar todos.
public record CursosCriticosRequest(
        @NotNull(message = "Debes enviar la lista de cursos")
        List<Integer> cursos
) {
}
