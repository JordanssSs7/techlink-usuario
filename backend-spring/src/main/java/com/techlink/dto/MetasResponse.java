package com.techlink.dto;

import java.util.List;

// HU-11: las metas de una semana por fecha de entrega, el progreso de esa semana, las metas atrasadas de semanas anteriores y las
// próximas pendientes de semanas siguientes (las 10 más cercanas), para que una meta que el mentor acordó para más adelante se vea ya.
public record MetasResponse(
        ProgresoSemanaResponse semana,
        List<MetaResponse> metas,
        List<MetaResponse> atrasadas,
        List<MetaResponse> proximas
) {
}
