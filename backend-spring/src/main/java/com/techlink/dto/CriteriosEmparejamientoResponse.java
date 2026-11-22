package com.techlink.dto;

import java.time.LocalDateTime;

// HU-61: los pesos vigentes, los iniciales (a los que se puede volver) y cuándo se cambiaron por última vez.
public record CriteriosEmparejamientoResponse(
        PesosEmparejamientoRequest pesos,
        PesosEmparejamientoRequest predeterminados,
        LocalDateTime actualizado
) {
}
