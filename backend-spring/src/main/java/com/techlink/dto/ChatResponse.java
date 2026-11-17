package com.techlink.dto;

// HU-04: respuesta del agente en el chat. "recomendacion" viene solo cuando el agente ya entendio los cursos y el
// horario del estudiante y buscó mentores; mientras tanto el agente solo conversa (pregunta lo que le falta).
public record ChatResponse(
        String respuesta,
        boolean generadoConIa,
        RecomendacionResponse recomendacion
) {
}
