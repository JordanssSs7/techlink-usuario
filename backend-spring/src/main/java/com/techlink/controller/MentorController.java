package com.techlink.controller;

import com.techlink.dto.PerfilMentorRequest;
import com.techlink.dto.PerfilMentorResponse;
import com.techlink.service.MentorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// HU-05: perfil del mentor. /api/mentor/** exige el rol MENTOR (SecurityConfig); 401 sin token, 403 con otro rol.
@RestController
@RequestMapping("/api/mentor")
public class MentorController {

    private final MentorService mentorService;

    public MentorController(MentorService mentorService) {
        this.mentorService = mentorService;
    }

    // GET /api/mentor/perfil: fortalezas, cursos, areas y todos los horarios (activos o no) del mentor dueño del token.
    @GetMapping("/perfil")
    public ResponseEntity<PerfilMentorResponse> obtenerPerfil(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(mentorService.obtenerPerfil(Integer.valueOf(jwt.getSubject())));
    }

    // PUT /api/mentor/perfil: guarda el perfil completo (reemplaza las listas). 400 con campos si hay datos invalidos.
    @PutMapping("/perfil")
    public ResponseEntity<PerfilMentorResponse> actualizarPerfil(@AuthenticationPrincipal Jwt jwt,
                                                                 @Valid @RequestBody PerfilMentorRequest request) {
        return ResponseEntity.ok(mentorService.actualizarPerfil(Integer.valueOf(jwt.getSubject()), request));
    }
}
