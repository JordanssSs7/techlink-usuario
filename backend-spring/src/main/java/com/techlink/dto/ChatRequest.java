package com.techlink.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

// HU-04: la conversacion con el agente. El servidor no guarda nada: el navegador manda los ultimos mensajes en cada turno.
public record ChatRequest(
        @NotNull(message = "Escribe un mensaje")
        @Size(min = 1, max = 12, message = "La conversación admite entre 1 y 12 mensajes")
        @Valid
        List<Mensaje> mensajes
) {

    // rol: "usuario" (el estudiante) o "agente" (la respuesta anterior de la IA).
    public record Mensaje(
            @NotNull(message = "Indica quién escribió el mensaje")
            @Pattern(regexp = "usuario|agente", message = "El rol debe ser usuario o agente")
            String rol,

            @NotBlank(message = "El mensaje no puede estar vacío")
            @Size(max = 500, message = "Cada mensaje admite hasta 500 caracteres")
            String texto
    ) {
    }
}
