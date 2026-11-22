package com.techlink.dto;

import java.util.List;

// HU-16: resultado de buscar mentores por curso, horario y calificación mínima.
// mensaje y sugerencias solo vienen cuando no hay resultados: explican qué pasó y qué filtro conviene ampliar.
public record BusquedaMentoresResponse(
        List<MentorRankingItem> mentores,
        int total,
        String mensaje,
        List<Sugerencia> sugerencias
) {

    // quitar: el filtro que se sugiere ampliar ("curso", "horario" o "calificacion"; "todos" para quitarlos todos).
    // resultados: cuántos mentores aparecerían sin ese filtro.
    public record Sugerencia(String quitar, String texto, int resultados) {
    }
}
