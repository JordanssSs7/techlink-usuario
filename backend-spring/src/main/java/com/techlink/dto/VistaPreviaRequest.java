package com.techlink.dto;

import jakarta.validation.Valid;

import java.util.List;

// HU-61: un caso de prueba para ver qué mentores saldrían con unos pesos: los cursos que el estudiante necesita reforzar y,
// si se quiere, sus horarios (si no se indican, se supone que está libre toda la semana).
public record VistaPreviaRequest(List<Integer> cursos, PesosEmparejamientoRequest pesos, List<RecomendacionRequest.@Valid Horario> horarios) {
}
