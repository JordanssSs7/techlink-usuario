package com.techlink.dto;

import java.util.List;

// HU-30: el centro de ayuda con las preguntas frecuentes agrupadas por tema.
public final class AyudaDtos {

    private AyudaDtos() {
    }

    public record Pregunta(Integer id, String pregunta, String respuesta) {
    }

    public record Tema(Integer id, String nombre, List<Pregunta> preguntas) {
    }

    // "total" es la cantidad de preguntas encontradas. "correoSoporte" es a donde escribir si no se encuentra la respuesta.
    public record AyudaResponse(String busqueda, int total, List<Tema> temas, String correoSoporte) {
    }
}
