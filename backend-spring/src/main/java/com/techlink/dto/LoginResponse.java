package com.techlink.dto;

// HU-02: Respuesta del login: el token JWT y los datos básicos del usuario (sin contraseña).
public record LoginResponse(
        String token,
        String tipo,
        long expiraEnSegundos,
        UsuarioSesion usuario
) {
    // Datos del usuario que el frontend usa para mostrar su nombre y elegir el panel según el rol.
    public record UsuarioSesion(
            Integer id,
            String nombres,
            String apellidos,
            String correoInstitucional,
            String rol
    ) {
    }
}
