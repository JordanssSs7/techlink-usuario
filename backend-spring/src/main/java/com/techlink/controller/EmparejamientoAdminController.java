package com.techlink.controller;

import com.techlink.dto.CriteriosEmparejamientoResponse;
import com.techlink.dto.PesosEmparejamientoRequest;
import com.techlink.dto.VistaPreviaRequest;
import com.techlink.dto.VistaPreviaResponse;
import com.techlink.service.AgenteRecomendacionService;
import com.techlink.service.CriteriosEmparejamientoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// HU-61: criterios del emparejamiento con IA, para el panel de administración. Solo ADMIN (SecurityConfig exige el rol en
// /api/admin/**): 401 sin token, 403 con otro rol, 400 si un peso está fuera de 0 a 100 o los tres son cero.
@RestController
@RequestMapping("/api/admin/emparejamiento")
public class EmparejamientoAdminController {

    private final CriteriosEmparejamientoService criterios;
    private final AgenteRecomendacionService recomendacion;

    public EmparejamientoAdminController(CriteriosEmparejamientoService criterios, AgenteRecomendacionService recomendacion) {
        this.criterios = criterios;
        this.recomendacion = recomendacion;
    }

    // Los pesos vigentes y los iniciales.
    @GetMapping("/criterios")
    public ResponseEntity<CriteriosEmparejamientoResponse> consultar() {
        return ResponseEntity.ok(criterios.consultar());
    }

    // Guarda los pesos nuevos (quedan en el historial de acciones del administrador).
    @PutMapping("/criterios")
    public ResponseEntity<CriteriosEmparejamientoResponse> guardar(@AuthenticationPrincipal Jwt jwt,
                                                                   @RequestBody PesosEmparejamientoRequest pesos) {
        return ResponseEntity.ok(criterios.guardar(Integer.valueOf(jwt.getSubject()), pesos));
    }

    // Los 3 mentores que saldrían con unos pesos para un caso de prueba, sin guardar nada.
    @PostMapping("/vista-previa")
    public ResponseEntity<VistaPreviaResponse> vistaPrevia(@Valid @RequestBody VistaPreviaRequest solicitud) {
        return ResponseEntity.ok(recomendacion.vistaPrevia(solicitud));
    }
}
