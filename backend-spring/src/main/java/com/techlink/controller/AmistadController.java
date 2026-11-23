package com.techlink.controller;

import com.techlink.exception.ReglaNegocioException;
import com.techlink.service.AmistadService;
import com.techlink.service.AmistadService.Perfil;
import com.techlink.service.AmistadService.Persona;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Seguidores y perfil de otras personas (como en GitHub). Requiere el token de un ESTUDIANTE o un MENTOR.
@RestController
@RequestMapping("/api")
public class AmistadController {

    private final AmistadService servicio;

    public AmistadController(AmistadService servicio) {
        this.servicio = servicio;
    }

    // El perfil de otra persona: lo básico para todos; la descripción y los intereses, solo para quien la sigue.
    @GetMapping("/usuarios/{id}/perfil-social")
    public ResponseEntity<Perfil> perfil(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        return ResponseEntity.ok(servicio.perfil(participante(jwt), id));
    }

    @GetMapping("/usuarios/{id}/seguidores")
    public ResponseEntity<List<Persona>> seguidores(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        participante(jwt);
        return ResponseEntity.ok(servicio.seguidores(id));
    }

    @GetMapping("/usuarios/{id}/siguiendo")
    public ResponseEntity<List<Persona>> siguiendo(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        participante(jwt);
        return ResponseEntity.ok(servicio.siguiendo(id));
    }

    @PostMapping("/usuarios/{id}/seguir")
    public ResponseEntity<Void> seguir(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        servicio.seguir(participante(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/usuarios/{id}/seguir")
    public ResponseEntity<Void> dejarDeSeguir(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        servicio.dejarDeSeguir(participante(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private static Integer participante(Jwt jwt) {
        String rol = jwt.getClaimAsString("rol");
        if (!"ESTUDIANTE".equals(rol) && !"MENTOR".equals(rol)) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Los seguidores son para estudiantes y mentores.");
        }
        return Integer.valueOf(jwt.getSubject());
    }
}
