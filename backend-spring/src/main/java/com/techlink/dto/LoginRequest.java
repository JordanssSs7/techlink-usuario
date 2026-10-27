package com.techlink.dto;

import jakarta.validation.constraints.NotBlank;

// HU-02: Credenciales que envía el frontend al iniciar sesión.
public record LoginRequest(
        @NotBlank(message = "El correo institucional es obligatorio")
        String correoInstitucional,

        @NotBlank(message = "La contraseña es obligatoria")
        String password
) {
}
