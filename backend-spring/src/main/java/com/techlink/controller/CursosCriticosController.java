package com.techlink.controller;

import com.techlink.dto.CursoResponse;
import com.techlink.dto.CursosCriticosRequest;
import com.techlink.service.CursoCriticoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// HU-04: cursos criticos del estudiante que inicio sesion. Requiere el token JWT.
// 401 sin token, 404 si el usuario no tiene perfil de estudiante.
@RestController
@RequestMapping("/api/estudiantes/me/cursos-criticos")
public class CursosCriticosController {

    private final CursoCriticoService cursoCriticoService;

    public CursosCriticosController(CursoCriticoService cursoCriticoService) {
        this.cursoCriticoService = cursoCriticoService;
    }

    // GET: los cursos criticos guardados (lista vacia si aun no eligio ninguno).
    @GetMapping
    public ResponseEntity<List<CursoResponse>> listar(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(cursoCriticoService.listar(Integer.valueOf(jwt.getSubject())));
    }

    // PUT: reemplaza los cursos criticos por los enviados. 400 con campos.cursos si hay un curso inexistente o mas de 5.
    @PutMapping
    public ResponseEntity<List<CursoResponse>> guardar(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody CursosCriticosRequest request) {
        return ResponseEntity.ok(cursoCriticoService.guardar(Integer.valueOf(jwt.getSubject()), request.cursos()));
    }
}
