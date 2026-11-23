package com.techlink.controller;

import com.techlink.exception.SesionException;
import com.techlink.service.SesionSolicitudService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// El mentor acepta o rechaza las solicitudes de sesión de los estudiantes. Solo para MENTOR; 409 si ya la respondió o hay choque de horario.
@RestController
@RequestMapping("/api/sesiones")
public class SesionSolicitudController {

    private final SesionSolicitudService solicitudes;

    public SesionSolicitudController(SesionSolicitudService solicitudes) {
        this.solicitudes = solicitudes;
    }

    @PutMapping("/{id}/aceptar")
    public ResponseEntity<Void> aceptar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        solicitudes.aceptar(mentor(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/rechazar")
    public ResponseEntity<Void> rechazar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        solicitudes.rechazar(mentor(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static Integer mentor(Jwt jwt) {
        if (!"MENTOR".equals(jwt.getClaimAsString("rol"))) {
            throw new SesionException(HttpStatus.FORBIDDEN, "Solo el mentor puede responder la solicitud.");
        }
        return Integer.valueOf(jwt.getSubject());
    }
}
