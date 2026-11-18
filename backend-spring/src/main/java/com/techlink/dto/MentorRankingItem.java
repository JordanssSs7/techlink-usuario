package com.techlink.dto;

import java.math.BigDecimal;
import java.util.List;

// HU-15: un mentor en el ranking, ordenado por promedio de estrellas y horas dictadas.
// promedio es null si todavía no tiene calificaciones.
public record MentorRankingItem(
        Integer posicion,
        Integer idMentor,
        Integer idUsuario,
        String nombres,
        String apellidos,
        String especialidad,
        BigDecimal promedio,
        Integer totalCalificaciones,
        Double horasDictadas,
        List<String> cursos,
        List<InsigniaResponse> insignias,
        boolean tieneFoto
) {
}
