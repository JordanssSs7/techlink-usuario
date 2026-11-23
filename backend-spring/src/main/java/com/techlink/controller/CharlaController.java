package com.techlink.controller;

import com.techlink.dto.CharlaResponse;
import com.techlink.dto.CharlasRecomendadasResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.service.CharlaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// HU-06 y HU-25: charlas virtuales para el estudiante. Requiere el token de un ESTUDIANTE (403 con otro rol).
// 404 si la charla no existe o está desactivada, 409 si ya terminó o si ya está inscrito.
@RestController
@RequestMapping("/api/charlas")
public class CharlaController {

    private final CharlaService charlaService;

    public CharlaController(CharlaService charlaService) {
        this.charlaService = charlaService;
    }

    // HU-06: charlas vigentes; las que coinciden con sus intereses o cursos críticos van en "recomendadas".
    @GetMapping("/recomendadas")
    public ResponseEntity<CharlasRecomendadasResponse> recomendadas(@AuthenticationPrincipal Jwt jwt) {
        soloEstudiante(jwt);
        Integer usuarioId = Integer.valueOf(jwt.getSubject());
        return ResponseEntity.ok("MENTOR".equals(jwt.getClaimAsString("rol")) ? charlaService.vigentesParaMentor(usuarioId)
                : charlaService.recomendadas(usuarioId));
    }

    // HU-25: "Mis charlas".
    @GetMapping("/mias")
    public ResponseEntity<List<CharlaResponse>> mias(@AuthenticationPrincipal Jwt jwt) {
        soloEstudiante(jwt);
        return ResponseEntity.ok(charlaService.mias(Integer.valueOf(jwt.getSubject())));
    }

    // HU-25: inscribirse.
    @PostMapping("/{id}/inscripcion")
    public ResponseEntity<CharlaResponse> inscribirse(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        soloEstudiante(jwt);
        return ResponseEntity.status(HttpStatus.CREATED).body(charlaService.inscribir(Integer.valueOf(jwt.getSubject()), id));
    }

    // Anular la inscripción.
    @DeleteMapping("/{id}/inscripcion")
    public ResponseEntity<Void> cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        soloEstudiante(jwt);
        charlaService.cancelar(Integer.valueOf(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }

    private static void soloEstudiante(Jwt jwt) {
        String rol = jwt.getClaimAsString("rol");
        if (!"ESTUDIANTE".equals(rol) && !"MENTOR".equals(rol)) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Solo los estudiantes y mentores pueden ver e inscribirse a charlas.");
        }
    }
}
