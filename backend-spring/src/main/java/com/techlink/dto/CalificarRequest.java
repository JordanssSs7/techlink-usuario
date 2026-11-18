package com.techlink.dto;

// HU-09: lo que el estudiante manda al calificar una sesión.
public record CalificarRequest(Integer estrellas, String comentario) {
}
