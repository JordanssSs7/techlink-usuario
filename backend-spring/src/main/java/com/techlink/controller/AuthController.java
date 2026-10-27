package com.techlink.controller;

import com.techlink.dto.GoogleLoginRequest;
import com.techlink.dto.LoginRequest;
import com.techlink.dto.LoginResponse;
import com.techlink.dto.RegisterRequest;
import com.techlink.dto.RegisterResponse;
import com.techlink.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Expone los endpoints de autenticacion bajo /api/auth.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // HU-01 - POST /api/auth/register: @Valid activa las validaciones del DTO; responde 201 si se crea.
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    // HU-02 - POST /api/auth/login: valida credenciales y devuelve el token JWT con los datos del usuario.
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // HU-02 (Google) - POST /api/auth/google: verifica el token de Google y devuelve nuestro JWT (igual que /login).
    @PostMapping("/google")
    public ResponseEntity<LoginResponse> google(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginConGoogle(request));
    }

    // HU-02 - GET /api/auth/me: ruta protegida. Devuelve quien es el dueño del token (id, correo y rol).
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(Map.of(
                "id", jwt.getSubject(),
                "correoInstitucional", jwt.getClaimAsString("correo"),
                "rol", jwt.getClaimAsString("rol")));
    }
}
