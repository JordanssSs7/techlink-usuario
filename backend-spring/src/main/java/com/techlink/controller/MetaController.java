package com.techlink.controller;

import com.techlink.dto.MarcarMetaRequest;
import com.techlink.dto.MetaActualizadaResponse;
import com.techlink.dto.MetaRequest;
import com.techlink.dto.MetaResponse;
import com.techlink.dto.MetasMentorResponse;
import com.techlink.dto.MetasResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.service.MetaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

// HU-11: metas académicas semanales. 401 sin token, 403 si el rol no corresponde, 404 si la meta no es del usuario.
@RestController
@RequestMapping("/api/metas")
public class MetaController {

    private final MetaService metaService;

    public MetaController(MetaService metaService) {
        this.metaService = metaService;
    }

    // Estudiante: las metas de una semana (?semana=AAAA-MM-DD, cualquier día de esa semana; por defecto la actual).
    @GetMapping
    public ResponseEntity<MetasResponse> listar(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String semana) {
        rol(jwt, "ESTUDIANTE");
        return ResponseEntity.ok(metaService.listar(id(jwt), fecha(semana)));
    }

    // Mentor: sus alumnos y las metas que les puso.
    @GetMapping("/mentor")
    public ResponseEntity<MetasMentorResponse> delMentor(@AuthenticationPrincipal Jwt jwt) {
        rol(jwt, "MENTOR");
        return ResponseEntity.ok(metaService.delMentor(id(jwt)));
    }

    // Estudiante (para sí) o mentor (para uno de sus alumnos).
    @PostMapping
    public ResponseEntity<MetaResponse> crear(@AuthenticationPrincipal Jwt jwt, @RequestBody MetaRequest solicitud) {
        String rol = rol(jwt, "ESTUDIANTE", "MENTOR");
        return ResponseEntity.status(HttpStatus.CREATED).body(metaService.crear(id(jwt), rol, solicitud));
    }

    // Estudiante: marcar o desmarcar. Devuelve el progreso de la semana de la meta ya recalculado.
    @PatchMapping("/{id}")
    public ResponseEntity<MetaActualizadaResponse> marcar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                          @RequestBody MarcarMetaRequest solicitud) {
        rol(jwt, "ESTUDIANTE");
        return ResponseEntity.ok(metaService.marcar(id(jwt), id, solicitud.completada()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        String rol = rol(jwt, "ESTUDIANTE", "MENTOR");
        metaService.eliminar(id(jwt), rol, id);
        return ResponseEntity.noContent().build();
    }

    private static Integer id(Jwt jwt) {
        return Integer.valueOf(jwt.getSubject());
    }

    // Devuelve el rol del token si es uno de los permitidos; si no, 403.
    private static String rol(Jwt jwt, String... permitidos) {
        String rol = jwt.getClaimAsString("rol");
        for (String p : permitidos) {
            if (p.equals(rol)) {
                return rol;
            }
        }
        throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Tu rol no puede usar las metas de esta forma.");
    }

    private static LocalDate fecha(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Fecha inválida: usa el formato AAAA-MM-DD.");
        }
    }
}
