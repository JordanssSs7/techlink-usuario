package com.techlink.dto;

import java.time.LocalDate;
import java.time.LocalTime;

// HU-06 y HU-25: una charla virtual tal como la ve el estudiante.
// razon: por qué se le recomienda (null si no coincide con su perfil). inscrito: si ya está anotado.
// vigente: false si la charla ya terminó. activa: false si el administrador la desactivó.
public record CharlaResponse(
        Integer id,
        String nombre,
        String tematica,
        String descripcion,
        String curso,
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin,
        String enlace,
        String razon,
        boolean inscrito,
        boolean vigente,
        boolean activa,
        String imagen
) {
}
