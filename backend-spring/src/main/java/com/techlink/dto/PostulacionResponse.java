package com.techlink.dto;

import java.time.LocalDateTime;

// HU-03: el estado de la postulación del estudiante para ser mentor.
// estado: NINGUNA (nunca postuló), EN_REVISION, APROBADA o RECHAZADA.
// puedePostular: si puede enviar una postulación nueva ahora; "mensaje" explica por qué no, cuando no puede.
public record PostulacionResponse(
        String estado,
        Integer id,
        String nombreArchivo,
        LocalDateTime fechaEnvio,
        String motivoRevision,
        boolean puedePostular,
        String mensaje
) {
}
