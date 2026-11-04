package com.techlink.controller;

import com.techlink.dto.LoginResponse;
import com.techlink.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// HU-02: GET /api/me devuelve el usuario dueño del token (para restaurar la sesion en el frontend). Requiere token.
@RestController
public class MeController {

    private final AuthService authService;

    public MeController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/api/me")
    public ResponseEntity<LoginResponse.UsuarioSesion> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.usuarioActual(Integer.valueOf(jwt.getSubject())));
    }
}
