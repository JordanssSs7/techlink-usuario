package com.techlink.dto;

import java.util.List;

// HU-61: los (máximo) 3 mentores que saldrían con unos pesos, del de mayor puntaje al de menor.
// candidatos = cuántos mentores cumplían las condiciones (dominan algún curso y coinciden en horario).
public record VistaPreviaResponse(PesosEmparejamientoRequest pesos, int candidatos, List<Mentor> mentores) {

    public record Mentor(
            Integer idMentor,
            String nombres,
            String apellidos,
            int puntaje,
            int cursosEnComun,
            int cursosPedidos,
            double calificacion,
            int totalCalificaciones,
            long minutosEnComun
    ) {
    }
}
