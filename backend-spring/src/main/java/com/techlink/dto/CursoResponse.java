package com.techlink.dto;

// HU-05: un curso del catalogo (GET /api/cursos) o un curso dominado por el mentor.
public record CursoResponse(Integer id, String nombre) {
}
