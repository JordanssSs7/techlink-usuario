package com.techlink.controller;

import com.techlink.dto.PostulacionResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.service.PostulacionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// HU-03: el estudiante postula a ser mentor. Requiere el token JWT de un ESTUDIANTE (403 con otro rol).
// 400 con "campos" si el archivo no es válido, 409 si ya tiene una postulación en revisión o no cumple el ciclo.
@RestController
@RequestMapping("/api/postulaciones")
public class PostulacionController {

    private final PostulacionService postulacionService;

    public PostulacionController(PostulacionService postulacionService) {
        this.postulacionService = postulacionService;
    }

    // Su postulación más reciente y si puede enviar una nueva.
    @GetMapping("/mia")
    public ResponseEntity<PostulacionResponse> mia(@AuthenticationPrincipal Jwt jwt) {
        soloEstudiante(jwt);
        return ResponseEntity.ok(postulacionService.estado(Integer.valueOf(jwt.getSubject())));
    }

    // Envía la postulación con el comprobante (multipart, campo "archivo": PDF, PNG o JPG de hasta 5 MB).
    @PostMapping
    public ResponseEntity<PostulacionResponse> postular(@AuthenticationPrincipal Jwt jwt,
                                                        @RequestParam("archivo") MultipartFile archivo,
                                                        @RequestParam(value = "comentario", required = false) String comentario) {
        soloEstudiante(jwt);
        PostulacionResponse respuesta = postulacionService.postular(Integer.valueOf(jwt.getSubject()), archivo, comentario);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    private static void soloEstudiante(Jwt jwt) {
        if (!"ESTUDIANTE".equals(jwt.getClaimAsString("rol"))) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Solo los estudiantes pueden postular a mentor.");
        }
    }
}
