package com.techlink.dto;

import java.time.LocalDateTime;

// HU-21: las notas de una sesión. "editable" es true solo para el mentor y mientras no pasen 24 horas de la sesión.
public record NotaResponse(String contenido, LocalDateTime actualizadaEn, boolean editable) {
}
