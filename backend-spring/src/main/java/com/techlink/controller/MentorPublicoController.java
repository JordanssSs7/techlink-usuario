package com.techlink.controller;

import com.techlink.dto.BusquedaMentoresResponse;
import com.techlink.dto.MentorPerfilPublico;
import com.techlink.dto.MentorRankingItem;
import com.techlink.service.MentorPublicoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// HU-14, HU-15 y HU-16: perfil público de los mentores, su ranking y la búsqueda con filtros. Requiere el token JWT (cualquier rol).
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

    // HU-16: busca mentores por curso (?cursoId=), horario (?dia=1..7, ?desde=HH:mm, ?hasta=HH:mm) y calificación mínima
    // (?calificacionMinima=0..5). Todos son opcionales y se combinan; sin filtros devuelve el ranking completo.
    // Sin resultados, la respuesta trae un mensaje y sugerencias de qué filtro ampliar. 400 con "campos" si un filtro es inválido.
    @GetMapping("/buscar")
    public ResponseEntity<BusquedaMentoresResponse> buscar(@RequestParam(required = false) Integer cursoId,
                                                           @RequestParam(required = false) Integer dia,
                                                           @RequestParam(required = false) String desde,
                                                           @RequestParam(required = false) String hasta,
                                                           @RequestParam(required = false) Double calificacionMinima) {
        return ResponseEntity.ok(mentorPublicoService.buscar(cursoId, dia, desde, hasta, calificacionMinima));
    }

    // Perfil público: cursos dominados, promedio de estrellas, últimos comentarios e insignias. 404 si no existe.
    @GetMapping("/{id}/perfil-publico")
    public ResponseEntity<MentorPerfilPublico> perfil(@PathVariable Integer id) {
        return ResponseEntity.ok(mentorPublicoService.perfil(id));
    }
}
