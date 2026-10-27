package com.techlink.controller;

import com.techlink.dto.GoogleLoginRequest;
import com.techlink.dto.LoginRequest;
import com.techlink.dto.LoginResponse;
import com.techlink.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Expone los endpoints de autenticacion bajo /api/auth.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // HU-02 - POST /api/auth/login: SOLO para administradores (correo de cualquier dominio y contraseña).
    // Alumnos y mentores entran con Google. Devuelve el token JWT con los datos del usuario.
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // HU-02 (Google) - POST /api/auth/google: verifica el token de Google y devuelve nuestro JWT (igual que /login).
    @PostMapping("/google")
    public ResponseEntity<LoginResponse> google(@Valid @RequestBody GoogleLoginRequest request) {
        return ResponseEntity.ok(authService.loginConGoogle(request));
    }
}
