package com.techlink.dto;

// Respuesta del registro: datos publicos del usuario creado (sin contrasena).
public record RegisterResponse(
        Integer id,
        String nombres,
        String apellidos,
        String correoInstitucional,
        String rol,
        Integer ciclo,
        String codigoEstudiante
) {
}
