package com.techlink.controller;

import com.techlink.dto.RecomendacionRequest;
import com.techlink.dto.RecomendacionResponse;
import com.techlink.service.AgenteRecomendacionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// HU-04: agente de IA que recomienda mentores. Requiere el token JWT (rol ESTUDIANTE o MENTOR, ver SecurityConfig).
@RestController
@RequestMapping("/api/agente")
public class AgenteController {

    private final AgenteRecomendacionService agenteService;

    public AgenteController(AgenteRecomendacionService agenteService) {
        this.agenteService = agenteService;
    }

    // POST /api/agente/recomendaciones: recibe cursos, horario y una nota opcional; devuelve hasta 3 mentores con su %.
    // 400 con campos si algo es invalido, 401 sin token, 403 si el rol no corresponde.
    @PostMapping("/recomendaciones")
    public ResponseEntity<RecomendacionResponse> recomendar(@AuthenticationPrincipal Jwt jwt,
                                                            @Valid @RequestBody RecomendacionRequest request) {
        return ResponseEntity.ok(agenteService.recomendar(Integer.valueOf(jwt.getSubject()), request));
    }
}
