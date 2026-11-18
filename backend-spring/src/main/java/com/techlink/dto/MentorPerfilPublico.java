package com.techlink.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// HU-14: el perfil público de un mentor, para que el estudiante decida con quién agendar.
// promedio es null si todavía no tiene calificaciones. horasDictadas cuenta solo las sesiones ya terminadas.
public record MentorPerfilPublico(
        Integer idMentor,
        Integer idUsuario,
        String nombres,
        String apellidos,
        String especialidad,
        String descripcion,
        String biografia,
        List<String> cursos,
        List<String> fortalezas,
        List<String> areas,
        BigDecimal promedio,
        Integer totalCalificaciones,
        Double horasDictadas,
        Integer sesionesCompletadas,
        List<InsigniaResponse> insignias,
        List<Comentario> comentarios,
        boolean tieneFoto
) {

    // Un comentario reciente. Del estudiante solo se muestra el primer nombre.
    public record Comentario(Integer estrellas, String comentario, String estudiante, LocalDate fecha) {
    }
}
