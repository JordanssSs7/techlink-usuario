package com.techlink.dto;

// El estado (frase corta con emoji) del perfil: se guarda solo, sin tocar la descripción, el ciclo ni los intereses.
public record EstadoRequest(String frase) {
}
