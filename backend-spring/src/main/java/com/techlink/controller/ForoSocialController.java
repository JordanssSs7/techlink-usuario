package com.techlink.controller;

import com.techlink.dto.ForoDtos.ConsultaResumen;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.service.ForoSocialService;
import com.techlink.service.ForoSocialService.Reacciones;
import com.techlink.service.ForoSocialService.Social;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// Foro social: reacciones a consultas y respuestas, y consultas guardadas. Para estudiantes y mentores.
@RestController
@RequestMapping("/api/foro")
public class ForoSocialController {

    private final ForoSocialService social;

    public ForoSocialController(ForoSocialService social) {
        this.social = social;
    }

    // Reacciones y guardado de una consulta (y de cada una de sus respuestas).
    @GetMapping("/consultas/{id}/social")
    public ResponseEntity<Social> social(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        return ResponseEntity.ok(social.social(participante(jwt), id));
    }

    // Body: { "reaccion": "ME_GUSTA" }. Repetir la misma reacción la quita.
    @PutMapping("/consultas/{id}/reaccion")
    public ResponseEntity<Reacciones> reaccionarConsulta(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                         @RequestBody Map<String, String> cuerpo) {
        return ResponseEntity.ok(social.reaccionarConsulta(participante(jwt), id, cuerpo.get("reaccion")));
    }

    @PutMapping("/respuestas/{id}/reaccion")
    public ResponseEntity<Reacciones> reaccionarRespuesta(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                          @RequestBody Map<String, String> cuerpo) {
        return ResponseEntity.ok(social.reaccionarRespuesta(participante(jwt), id, cuerpo.get("reaccion")));
    }

    @PostMapping("/consultas/{id}/guardar")
    public ResponseEntity<Void> guardar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        social.guardar(participante(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/consultas/{id}/guardar")
    public ResponseEntity<Void> quitarGuardado(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        social.quitarGuardado(participante(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/guardados")
    public ResponseEntity<List<ConsultaResumen>> guardados(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(social.guardadas(participante(jwt)));
    }

    private static Integer participante(Jwt jwt) {
        String rol = jwt.getClaimAsString("rol");
        if (!"ESTUDIANTE".equals(rol) && !"MENTOR".equals(rol)) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "El foro es para estudiantes y mentores.");
        }
        return Integer.valueOf(jwt.getSubject());
    }
}
