package com.techlink.dto;

// HU-29: el reporte registrado.
public record ReporteResponse(Integer id, String tipo, String estado, String mensaje) {
}
