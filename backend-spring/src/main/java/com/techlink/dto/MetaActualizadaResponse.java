package com.techlink.dto;

// HU-11: la meta después de marcarla o desmarcarla, con el progreso de su semana ya recalculado.
public record MetaActualizadaResponse(MetaResponse meta, ProgresoSemanaResponse progreso) {
}
