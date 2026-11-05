package com.techlink.controller;

import com.techlink.dto.CursoResponse;
import com.techlink.dto.PerfilMentorResponse.HorarioResponse;
import com.techlink.service.MentorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// HU-05: consultas abiertas a cualquier usuario con sesion (estudiantes incluidos). Las usan HU-04 y HU-07.
@RestController
public class CatalogoController {

    private final MentorService mentorService;

    public CatalogoController(MentorService mentorService) {
        this.mentorService = mentorService;
    }

    // GET /api/cursos: catalogo de cursos activos.
    @GetMapping("/api/cursos")
    public ResponseEntity<List<CursoResponse>> cursos() {
        return ResponseEntity.ok(mentorService.listarCursos());
    }

    // GET /api/mentores/{id}/disponibilidad: solo los horarios activos del mentor. 404 si el mentor no existe.
    @GetMapping("/api/mentores/{idMentor}/disponibilidad")
    public ResponseEntity<List<HorarioResponse>> disponibilidad(@PathVariable Integer idMentor) {
        return ResponseEntity.ok(mentorService.horariosActivos(idMentor));
    }
}
