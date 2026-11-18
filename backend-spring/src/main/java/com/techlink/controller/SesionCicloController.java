package com.techlink.controller;

import com.techlink.dto.CalificacionResponse;
import com.techlink.dto.CalificarRequest;
import com.techlink.dto.NotaRequest;
import com.techlink.dto.NotaResponse;
import com.techlink.dto.ReprogramarRequest;
import com.techlink.dto.SesionHistorialResponse;
import com.techlink.dto.SesionResponse;
import com.techlink.exception.SesionException;
import com.techlink.service.CalificacionService;
import com.techlink.service.SesionCicloService;
import com.techlink.service.SesionHistorialService;
import com.techlink.service.SesionNotaService;
import com.techlink.service.SesionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// HU-09, HU-19, HU-20 y HU-21: lo que pasa con una sesión después de agendarla.
// 401 sin token, 403 si el rol no corresponde, 404 si la sesión no es del usuario.
@RestController
@RequestMapping("/api/sesiones")
public class SesionCicloController {

    private final SesionHistorialService historialService;
    private final CalificacionService calificacionService;
    private final SesionCicloService cicloService;
    private final SesionNotaService notaService;
    private final SesionService sesionService;

    public SesionCicloController(SesionHistorialService historialService,
                                 CalificacionService calificacionService,
                                 SesionCicloService cicloService,
                                 SesionNotaService notaService,
                                 SesionService sesionService) {
        this.historialService = historialService;
        this.calificacionService = calificacionService;
        this.cicloService = cicloService;
        this.notaService = notaService;
        this.sesionService = sesionService;
    }

    // HU-20: historial del estudiante o del mentor. ?estado=PROGRAMADA|COMPLETADA|CANCELADA filtra.
    @GetMapping("/historial")
    public ResponseEntity<List<SesionHistorialResponse>> historial(@AuthenticationPrincipal Jwt jwt,
                                                                   @RequestParam(required = false) String estado) {
        String rol = rolDe(jwt, "ESTUDIANTE", "MENTOR");
        return ResponseEntity.ok(historialService.listar(idDe(jwt), rol, estado));
    }

    // HU-09: el estudiante califica una sesión que ya terminó.
    @PostMapping("/{id}/calificacion")
    public ResponseEntity<CalificacionResponse> calificar(@AuthenticationPrincipal Jwt jwt,
                                                          @PathVariable Integer id,
                                                          @RequestBody CalificarRequest request) {
        rolDe(jwt, "ESTUDIANTE");
        CalificacionResponse respuesta = calificacionService.calificar(
                idDe(jwt), id, request.estrellas(), request.comentario());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    // HU-19: el estudiante cancela su sesión (mínimo 2 horas antes).
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<SesionResponse> cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        rolDe(jwt, "ESTUDIANTE");
        return ResponseEntity.ok(sesionService.convertirARespuesta(cicloService.cancelar(idDe(jwt), id)));
    }

    // HU-19: el estudiante mueve su sesión a otro horario del mismo mentor (mínimo 2 horas antes).
    @PatchMapping("/{id}/reprogramar")
    public ResponseEntity<SesionResponse> reprogramar(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable Integer id,
                                                      @RequestBody ReprogramarRequest request) {
        rolDe(jwt, "ESTUDIANTE");
        return ResponseEntity.ok(sesionService.convertirARespuesta(cicloService.reprogramar(
                idDe(jwt), id, request.disponibilidadId(), request.fecha(), request.horaInicio(), request.horaFin())));
    }

    // HU-21: el mentor guarda o edita las notas de la sesión.
    @PutMapping("/{id}/notas")
    public ResponseEntity<NotaResponse> guardarNota(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable Integer id,
                                                    @RequestBody NotaRequest request) {
        rolDe(jwt, "MENTOR");
        return ResponseEntity.ok(notaService.guardar(idDe(jwt), id, request.contenido()));
    }

    // HU-21: el mentor y el estudiante de la sesión leen las notas.
    @GetMapping("/{id}/notas")
    public ResponseEntity<NotaResponse> obtenerNota(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        rolDe(jwt, "ESTUDIANTE", "MENTOR");
        return ResponseEntity.ok(notaService.obtener(idDe(jwt), id));
    }

    private static Integer idDe(Jwt jwt) {
        return Integer.valueOf(jwt.getSubject());
    }

    // Devuelve el rol del token si es uno de los permitidos; si no, 403.
    private static String rolDe(Jwt jwt, String... permitidos) {
        String rol = jwt.getClaimAsString("rol");
        for (String p : permitidos) {
            if (p.equals(rol)) {
                return rol;
            }
        }
        throw new SesionException(HttpStatus.FORBIDDEN, "No tienes permiso para esta acción.");
    }
}
