package com.techlink.controller;

import com.techlink.dto.CicloRequest;
import com.techlink.dto.LoginResponse;
import com.techlink.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// HU-02 (Google): datos del estudiante que inicio sesion. Requiere el token JWT.
@RestController
@RequestMapping("/api/estudiantes")
public class EstudianteController {

    private final AuthService authService;

    public EstudianteController(AuthService authService) {
        this.authService = authService;
    }

    // PATCH /api/estudiantes/me: guarda el ciclo (1 a 6) del estudiante dueño del token.
    // 400 con campos.ciclo si no es de 1 a 6, 401 sin token, 404 si no tiene perfil de estudiante.
    @PatchMapping("/me")
    public ResponseEntity<LoginResponse.UsuarioSesion> actualizarCiclo(@AuthenticationPrincipal Jwt jwt,
                                                                       @Valid @RequestBody CicloRequest request) {
        Integer idUsuario = Integer.valueOf(jwt.getSubject());
        return ResponseEntity.ok(authService.actualizarCiclo(idUsuario, request.ciclo()));
    }
}
