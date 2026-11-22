package com.techlink.dto;

import java.util.List;

// HU-11: lo que ve el mentor: los alumnos a los que puede ponerles metas y las metas que ya puso.
public record MetasMentorResponse(List<EstudianteResumen> estudiantes, List<MetaResponse> metas) {

    public record EstudianteResumen(Integer idUsuario, String nombre) {
    }
}
