package com.techlink.controller;

import com.techlink.dto.ReporteRequest;
import com.techlink.dto.ReporteResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.service.ReporteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// HU-29: reportar a un usuario o una sesión. Requiere el token de un ESTUDIANTE o MENTOR (403 con otro rol).
// 400 si los datos no son válidos, 404 si la sesión o el usuario no existen, 409 si ya hay un reporte igual en revisión.
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    @PostMapping
    public ResponseEntity<ReporteResponse> reportar(@AuthenticationPrincipal Jwt jwt, @RequestBody ReporteRequest solicitud) {
        String rol = jwt.getClaimAsString("rol");
        if (!"ESTUDIANTE".equals(rol) && !"MENTOR".equals(rol)) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Solo estudiantes y mentores pueden enviar reportes.");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reporteService.reportar(Integer.valueOf(jwt.getSubject()), solicitud));
    }
}
