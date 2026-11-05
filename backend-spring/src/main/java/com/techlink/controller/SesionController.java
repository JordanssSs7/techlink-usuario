
package com.techlink.controller;

import com.techlink.dto.SesionResponse;
import com.techlink.model.Sesion;
import com.techlink.service.SesionService;
import com.techlink.dto.AgendarSesionRequest;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;



import java.util.List;

@RestController
@RequestMapping("/api/sesiones")
public class SesionController {

    private final SesionService sesionService;

    public SesionController(SesionService sesionService) {
        this.sesionService = sesionService;
    }


    // HU-07: registrar una sesión para el estudiante autenticado.
    @PostMapping
    public ResponseEntity<SesionResponse> agendarSesion(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AgendarSesionRequest request
    ) {

        // Verificar que el usuario tenga el rol ESTUDIANTE.
        if (!"ESTUDIANTE".equals(jwt.getClaimAsString("rol"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Obtener el ID real del usuario desde el JWT.
        Integer usuarioId = Integer.valueOf(jwt.getSubject());

        // Registrar la sesión utilizando el servicio.

        Sesion sesion;

        try {
            sesion = sesionService.agendarSesion(
                    usuarioId,
                    request.getMentorId(),
                    request.getDisponibilidadId(),
                    request.getFecha(),
                    request.getHoraInicio(),
                    request.getHoraFin(),
                    request.getEnlaceReunion()
            );
        } catch (IllegalArgumentException e) {

            HttpStatus estado = e.getMessage() != null
                    && e.getMessage().contains("ya tiene una sesión reservada")
                    ? HttpStatus.CONFLICT
                    : HttpStatus.BAD_REQUEST;

            throw new ResponseStatusException(estado, e.getMessage(), e);
        }

        // Convertir la entidad en un DTO de respuesta.
        SesionResponse respuesta =
                sesionService.convertirARespuesta(sesion);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(respuesta);
    }


    // HU-07: consultar las sesiones del estudiante autenticado.
    @GetMapping
    public ResponseEntity<List<SesionResponse>> listarSesiones(
            @AuthenticationPrincipal Jwt jwt
    ) {

        // Verificar que el usuario sea estudiante.
        if (!"ESTUDIANTE".equals(jwt.getClaimAsString("rol"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Obtener el ID del usuario autenticado desde el JWT.
        Integer usuarioId = Integer.valueOf(jwt.getSubject());

        // Consultar únicamente las sesiones de ese estudiante.
        List<Sesion> sesiones = sesionService.listarSesiones(usuarioId);

        // Convertir las entidades en respuestas para el frontend.
        List<SesionResponse> respuesta = sesiones.stream()
                .map(sesionService::convertirARespuesta)
                .toList();

        return ResponseEntity.ok(respuesta);
    }
}
