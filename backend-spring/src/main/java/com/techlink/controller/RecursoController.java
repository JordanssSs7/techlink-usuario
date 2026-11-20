package com.techlink.controller;

import com.techlink.dto.RecursoDtos.Descarga;
import com.techlink.dto.RecursoDtos.NuevoCodigo;
import com.techlink.dto.RecursoDtos.NuevoEnlace;
import com.techlink.dto.RecursoDtos.RecursoResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.service.RecursoService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

// HU-18: recursos que el mentor comparte en una sesión (archivo, enlace o código). Requiere el token JWT.
// Solo el mentor de la sesión agrega o quita recursos; el mentor y el estudiante de la sesión los ven y descargan.
@RestController
@RequestMapping("/api")
public class RecursoController {

    private final RecursoService recursoService;

    public RecursoController(RecursoService recursoService) {
        this.recursoService = recursoService;
    }

    @GetMapping("/sesiones/{id}/recursos")
    public ResponseEntity<List<RecursoResponse>> listar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        return ResponseEntity.ok(recursoService.listar(idDe(jwt, "ESTUDIANTE", "MENTOR"), id));
    }

    @PostMapping("/sesiones/{id}/recursos/enlace")
    public ResponseEntity<RecursoResponse> agregarEnlace(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                         @RequestBody NuevoEnlace request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recursoService.agregarEnlace(idDe(jwt, "MENTOR"), id, request));
    }

    @PostMapping("/sesiones/{id}/recursos/codigo")
    public ResponseEntity<RecursoResponse> agregarCodigo(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                         @RequestBody NuevoCodigo request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recursoService.agregarCodigo(idDe(jwt, "MENTOR"), id, request));
    }

    // Archivo PDF, PNG o JPG de hasta 10 MB (multipart: "archivo", "titulo" y "descripcion" opcional).
    @PostMapping("/sesiones/{id}/recursos/archivo")
    public ResponseEntity<RecursoResponse> agregarArchivo(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id,
                                                          @RequestParam("archivo") MultipartFile archivo,
                                                          @RequestParam(value = "titulo", required = false) String titulo,
                                                          @RequestParam(value = "descripcion", required = false) String descripcion) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recursoService.agregarArchivo(idDe(jwt, "MENTOR"), id, titulo, descripcion, archivo));
    }

    // Descarga del archivo de un recurso.
    @GetMapping("/recursos/{id}/archivo")
    public ResponseEntity<byte[]> descargar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        Descarga d = recursoService.descargar(idDe(jwt, "ESTUDIANTE", "MENTOR"), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.nombre(), StandardCharsets.UTF_8).build().toString())
                .body(d.contenido());
    }

    @DeleteMapping("/recursos/{id}")
    public ResponseEntity<Void> eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        recursoService.eliminar(idDe(jwt, "MENTOR"), id);
        return ResponseEntity.noContent().build();
    }

    private static Integer idDe(Jwt jwt, String... permitidos) {
        String rol = jwt.getClaimAsString("rol");
        for (String p : permitidos) {
            if (p.equals(rol)) {
                return Integer.valueOf(jwt.getSubject());
            }
        }
        throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "No tienes permiso para esta acción.");
    }
}
