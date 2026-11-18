package com.techlink.dto;

import java.time.LocalDateTime;
import java.util.List;

// HU-13: el perfil del usuario. "fotoActualizada" sirve para que el navegador pida la foto de nuevo cuando cambia.
public record PerfilResponse(
        Integer id,
        String nombres,
        String apellidos,
        String correoInstitucional,
        String rol,
        String descripcion,
        Integer ciclo,
        List<String> intereses,
        boolean tieneFoto,
        LocalDateTime fotoActualizada
) {
}
