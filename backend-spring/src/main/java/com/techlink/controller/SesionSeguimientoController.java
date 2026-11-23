package com.techlink.controller;

import com.techlink.dto.SesionResponse;
import com.techlink.model.Sesion;
import com.techlink.service.SesionService;
import com.techlink.service.SesionSeguimientoService;
import com.techlink.service.SesionSeguimientoService.EstudianteSeguimiento;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// Sesiones de seguimiento que agenda el mentor (después de la primera reunión) en cualquier día y hora. Solo para MENTOR.
@RestController
@RequestMapping("/api/sesiones/seguimiento")
public class SesionSeguimientoController {

    public record Peticion(Integer estudianteId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, String enlaceReunion) {
    }

    private final SesionSeguimientoService seguimiento;
    private final SesionService sesionService;

    public SesionSeguimientoController(SesionSeguimientoService seguimiento, SesionService sesionService) {
        this.seguimiento = seguimiento;
        this.sesionService = sesionService;
    }

    // Los estudiantes con los que el mentor ya tuvo una sesión.
    @GetMapping("/estudiantes")
    public ResponseEntity<List<EstudianteSeguimiento>> estudiantes(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(seguimiento.estudiantes(mentor(jwt)));
    }

    @PostMapping
    public ResponseEntity<SesionResponse> agendar(@AuthenticationPrincipal Jwt jwt, @RequestBody Peticion peticion) {
        Integer usuarioId = mentor(jwt);
        try {
            Sesion sesion = seguimiento.agendar(usuarioId, peticion.estudianteId(), peticion.fecha(), peticion.horaInicio(),
                    peticion.horaFin(), peticion.enlaceReunion());
            return ResponseEntity.status(HttpStatus.CREATED).body(sesionService.convertirARespuesta(sesion));
        } catch (IllegalArgumentException e) {
            HttpStatus estado = e.getMessage() != null && e.getMessage().contains("ya tiene una sesión reservada")
                    ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
            throw new ResponseStatusException(estado, e.getMessage(), e);
        }
    }

    private static Integer mentor(Jwt jwt) {
        if (!"MENTOR".equals(jwt.getClaimAsString("rol"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo los mentores pueden agendar sesiones de seguimiento.");
        }
        return Integer.valueOf(jwt.getSubject());
    }
}
