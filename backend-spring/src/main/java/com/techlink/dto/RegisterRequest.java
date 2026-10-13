package com.techlink.dto;

import jakarta.validation.constraints.*;

// Datos que envia el frontend al registrarse; las anotaciones validan cada campo.
public record RegisterRequest(
        @NotBlank(message = "Los nombres son obligatorios")
        @Size(max = 100) String nombres,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 100) String apellidos,

        @NotBlank(message = "El correo institucional es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 150) String correoInstitucional,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres") String password,

        @NotNull(message = "El ciclo es obligatorio")
        @Min(value = 1, message = "El ciclo mínimo es 1")
        @Max(value = 10, message = "El ciclo máximo es 10") Integer ciclo,

        @Size(max = 20) String codigoEstudiante
) {
}
