package com.techlink.dto;

// HU-61: el peso (de 0 a 100) de cada criterio con el que se ordena a los mentores.
public record PesosEmparejamientoRequest(Integer cursos, Integer calificacion, Integer disponibilidad) {
}
