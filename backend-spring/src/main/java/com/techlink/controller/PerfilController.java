package com.techlink.controller;

import com.techlink.dto.PerfilRequest;
import com.techlink.dto.PerfilResponse;
import com.techlink.model.UsuarioFoto;
import com.techlink.service.PerfilService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

// HU-13: perfil del usuario que inició sesión (cualquier rol) y fotos de perfil. Requiere el token JWT.
// 400 con "campos" si falta un dato obligatorio o la foto no es válida, 401 sin token.
@RestController
@RequestMapping("/api")
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    @GetMapping("/perfil")
    public ResponseEntity<PerfilResponse> obtener(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(perfilService.obtener(idDe(jwt)));
    }

    // Guarda descripción, ciclo (solo estudiantes) e intereses; devuelve el perfil ya actualizado.
    @PutMapping("/perfil")
    public ResponseEntity<PerfilResponse> actualizar(@AuthenticationPrincipal Jwt jwt, @RequestBody PerfilRequest request) {
        return ResponseEntity.ok(perfilService.actualizar(idDe(jwt), request));
    }

    // Foto de perfil: JPG o PNG de hasta 2 MB (multipart, campo "foto").
    @PostMapping("/perfil/foto")
    public ResponseEntity<PerfilResponse> subirFoto(@AuthenticationPrincipal Jwt jwt, @RequestParam("foto") MultipartFile foto) {
        return ResponseEntity.ok(perfilService.guardarFoto(idDe(jwt), foto));
    }

    // Quita la foto de perfil (se vuelven a ver las iniciales).
    @org.springframework.web.bind.annotation.DeleteMapping("/perfil/foto")
    public ResponseEntity<PerfilResponse> quitarFoto(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(perfilService.quitarFoto(idDe(jwt)));
    }

    // Foto de portada (como la de Facebook): JPG o PNG de hasta 4 MB (multipart, campo "portada").
    @PostMapping("/perfil/portada")
    public ResponseEntity<PerfilResponse> subirPortada(@AuthenticationPrincipal Jwt jwt, @RequestParam("portada") MultipartFile portada) {
        return ResponseEntity.ok(perfilService.guardarPortada(idDe(jwt), portada));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/perfil/portada")
    public ResponseEntity<PerfilResponse> quitarPortada(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(perfilService.quitarPortada(idDe(jwt)));
    }

    @GetMapping("/usuarios/{id}/portada")
    public ResponseEntity<byte[]> portada(@PathVariable Integer id) {
        return perfilService.portada(id)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.getTipoContenido()))
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                        .body(p.getContenido()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // La foto de cualquier usuario (se ve en perfiles públicos y rankings). Cualquier usuario con sesión puede pedirla.
    @GetMapping("/usuarios/{id}/foto")
    public ResponseEntity<byte[]> foto(@PathVariable Integer id) {
        return perfilService.foto(id)
                .map((UsuarioFoto f) -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(f.getTipoContenido()))
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                        .body(f.getContenido()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static Integer idDe(Jwt jwt) {
        return Integer.valueOf(jwt.getSubject());
    }
}
