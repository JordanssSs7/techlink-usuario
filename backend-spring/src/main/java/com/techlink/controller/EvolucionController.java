package com.techlink.controller;

import com.techlink.dto.AutoevaluacionRequest;
import com.techlink.dto.AutoevaluacionResponse;
import com.techlink.dto.EvolucionResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.service.EvolucionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// HU-12: panel de evolución del estudiante y sus autoevaluaciones. Requiere el token de un ESTUDIANTE (403 con otro rol).
@RestController
@RequestMapping("/api")
public class EvolucionController {

    private final EvolucionService evolucionService;

    public EvolucionController(EvolucionService evolucionService) {
        this.evolucionService = evolucionService;
    }

    // ?agrupar=semana|mes y ?periodos=N (cuántas semanas o meses, terminando en el actual).
    @GetMapping("/evolucion")
    public ResponseEntity<EvolucionResponse> evolucion(@AuthenticationPrincipal Jwt jwt,
                                                       @RequestParam(required = false) String agrupar,
                                                       @RequestParam(required = false) Integer periodos) {
        soloEstudiante(jwt);
        return ResponseEntity.ok(evolucionService.evolucion(id(jwt), agrupar, periodos));
    }

    @PostMapping("/autoevaluaciones")
    public ResponseEntity<AutoevaluacionResponse> registrar(@AuthenticationPrincipal Jwt jwt, @RequestBody AutoevaluacionRequest solicitud) {
        soloEstudiante(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(evolucionService.registrar(id(jwt), solicitud));
    }

    @GetMapping("/autoevaluaciones")
    public ResponseEntity<List<AutoevaluacionResponse>> recientes(@AuthenticationPrincipal Jwt jwt) {
        soloEstudiante(jwt);
        return ResponseEntity.ok(evolucionService.recientes(id(jwt)));
    }

    @DeleteMapping("/autoevaluaciones/{id}")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        soloEstudiante(jwt);
        evolucionService.eliminar(id(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static Integer id(Jwt jwt) {
        return Integer.valueOf(jwt.getSubject());
    }

    private static void soloEstudiante(Jwt jwt) {
        if (!"ESTUDIANTE".equals(jwt.getClaimAsString("rol"))) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Solo los estudiantes tienen panel de evolución.");
        }
    }
}
