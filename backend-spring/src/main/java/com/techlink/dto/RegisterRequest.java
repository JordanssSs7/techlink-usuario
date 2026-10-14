package com.techlink.dto;

import jakarta.validation.constraints.*;

// Datos que envia el frontend al registrarse; las anotaciones validan cada campo.
public record RegisterRequest(
        @NotBlank(message = "Los nombres son obligatorios")
        @Size(min = 2, max = 100, message = "Los nombres deben tener entre 2 y 100 caracteres")
        @Pattern(regexp = "^\\p{L}+([ '\\-]\\p{L}+)*$", message = "Los nombres solo pueden contener letras")
        String nombres,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(min = 2, max = 100, message = "Los apellidos deben tener entre 2 y 100 caracteres")
        @Pattern(regexp = "^\\p{L}+([ '\\-]\\p{L}+)*$", message = "Los apellidos solo pueden contener letras")
        String apellidos,

        @NotBlank(message = "El correo institucional es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 150) String correoInstitucional,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$", message = "La contraseña debe incluir al menos una letra y un número")
        String password,

        // Tecsup tiene 6 ciclos; el registro es para cualquier ciclo de 1 a 6.
        @NotNull(message = "El ciclo es obligatorio")
        @Min(value = 1, message = "El ciclo debe estar entre 1 y 6")
        @Max(value = 6, message = "El ciclo debe estar entre 1 y 6") Integer ciclo,

        @Size(max = 20, message = "El código no puede superar 20 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9]*$", message = "El código solo puede tener letras y números")
        String codigoEstudiante
) {
}
