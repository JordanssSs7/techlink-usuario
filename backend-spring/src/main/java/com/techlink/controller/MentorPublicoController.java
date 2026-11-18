package com.techlink.controller;

import com.techlink.dto.MentorPerfilPublico;
import com.techlink.dto.MentorRankingItem;
import com.techlink.service.MentorPublicoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// HU-14 y HU-15: perfil público de los mentores y su ranking. Requiere el token JWT (cualquier rol).
@RestController
@RequestMapping("/api/mentores")
public class MentorPublicoController {

    private final MentorPublicoService mentorPublicoService;

    public MentorPublicoController(MentorPublicoService mentorPublicoService) {
        this.mentorPublicoService = mentorPublicoService;
    }

    // Ranking de mentores por promedio de estrellas y horas dictadas, con sus insignias.
    @GetMapping("/ranking")
    public ResponseEntity<List<MentorRankingItem>> ranking() {
        return ResponseEntity.ok(mentorPublicoService.ranking());
    }

    // Perfil público: cursos dominados, promedio de estrellas, últimos comentarios e insignias. 404 si no existe.
    @GetMapping("/{id}/perfil-publico")
    public ResponseEntity<MentorPerfilPublico> perfil(@PathVariable Integer id) {
        return ResponseEntity.ok(mentorPublicoService.perfil(id));
    }
}
