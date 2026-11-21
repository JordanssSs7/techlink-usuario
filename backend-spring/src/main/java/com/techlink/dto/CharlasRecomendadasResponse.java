package com.techlink.dto;

import java.util.List;

// HU-06: las charlas vigentes separadas en las que coinciden con el perfil del estudiante (recomendadas) y las demás.
// perfilCompleto es false si todavía no tiene intereses ni cursos críticos: en ese caso no hay forma de recomendarle.
public record CharlasRecomendadasResponse(
        List<CharlaResponse> recomendadas,
        List<CharlaResponse> otras,
        boolean perfilCompleto
) {
}
