package com.techlink.controller;

import com.techlink.dto.ForoDtos.ConsultaDetalle;
import com.techlink.dto.ForoDtos.ConsultaResumen;
import com.techlink.dto.ForoDtos.EdicionConsulta;
import com.techlink.dto.ForoDtos.EdicionRespuesta;
import com.techlink.dto.ForoDtos.NuevaConsulta;
import com.techlink.dto.ForoDtos.NuevaRespuesta;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.service.ForoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// HU-17: foro de consultas por curso. Requiere el token JWT de un ESTUDIANTE o un MENTOR (403 con otro rol).
// 400 con "campos" si falta o sobra algo, 404 si la consulta no existe (o está oculta), 429 si publica demasiado rápido.
@RestController
@RequestMapping("/api/foro/consultas")
public class ForoController {

    private final ForoService foroService;

    public ForoController(ForoService foroService) {
        this.foroService = foroService;
    }

    // Las consultas más recientes. ?cursoId= filtra por curso y ?q= busca una palabra en el título o el contenido.
    @GetMapping
    public ResponseEntity<List<ConsultaResumen>> listar(@AuthenticationPrincipal Jwt jwt,
                                                        @RequestParam(required = false) Integer cursoId,
                                                        @RequestParam(required = false) String q) {
        participante(jwt);
        return ResponseEntity.ok(foroService.listar(cursoId, q));
    }

    // Una consulta con sus respuestas: la marcada como útil primero.
    @GetMapping("/{id}")
    public ResponseEntity<ConsultaDetalle> detalle(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        return ResponseEntity.ok(foroService.detalle(participante(jwt), id));
    }

    @PostMapping
    public ResponseEntity<ConsultaDetalle> crear(@AuthenticationPrincipal Jwt jwt, @RequestBody NuevaConsulta request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(foroService.crear(participante(jwt), request));
    }

    // Quien hizo la consulta la edita (403 si es de otro, 422 si la moderación no la deja) o la elimina (204).
    @PutMapping("/{id}")
    public ResponseEntity<ConsultaDetalle> editar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                  @RequestBody EdicionConsulta request) {
        return ResponseEntity.ok(foroService.editarConsulta(participante(jwt), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        foroService.eliminarConsulta(participante(jwt), id);
        return ResponseEntity.noContent().build();
    }

    // Quien escribió la respuesta la edita o la elimina (devuelve la consulta actualizada).
    @PutMapping("/{id}/respuestas/{respuestaId}")
    public ResponseEntity<ConsultaDetalle> editarRespuesta(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                           @PathVariable Integer respuestaId, @RequestBody EdicionRespuesta request) {
        return ResponseEntity.ok(foroService.editarRespuesta(participante(jwt), id, respuestaId, request));
    }

    @DeleteMapping("/{id}/respuestas/{respuestaId}")
    public ResponseEntity<ConsultaDetalle> eliminarRespuesta(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                             @PathVariable Integer respuestaId) {
        return ResponseEntity.ok(foroService.eliminarRespuesta(participante(jwt), id, respuestaId));
    }

    @PostMapping("/{id}/respuestas")
    public ResponseEntity<ConsultaDetalle> responder(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                     @RequestBody NuevaRespuesta request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(foroService.responder(participante(jwt), id, request));
    }

    // Marca (o desmarca) la respuesta como útil. Solo lo puede hacer el autor de la consulta.
    @PatchMapping("/{id}/respuestas/{idRespuesta}/util")
    public ResponseEntity<ConsultaDetalle> marcarUtil(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                      @PathVariable Integer idRespuesta) {
        return ResponseEntity.ok(foroService.marcarUtil(participante(jwt), id, idRespuesta));
    }

    // Devuelve el id del usuario si es estudiante o mentor.
    private static Integer participante(Jwt jwt) {
        String rol = jwt.getClaimAsString("rol");
        if (!"ESTUDIANTE".equals(rol) && !"MENTOR".equals(rol)) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "El foro es para estudiantes y mentores.");
        }
        return Integer.valueOf(jwt.getSubject());
    }
}
