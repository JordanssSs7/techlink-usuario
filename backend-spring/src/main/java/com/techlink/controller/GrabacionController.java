package com.techlink.controller;

import com.techlink.exception.SesionException;
import com.techlink.service.GrabacionService;
import com.techlink.service.GrabacionTicketService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Grabaciones de las sesiones. El mentor sube la grabación por partes; el estudiante y el mentor la ven desde su historial.
@RestController
@RequestMapping("/api")
public class GrabacionController {

    private final GrabacionService grabaciones;
    private final GrabacionTicketService tickets;

    public GrabacionController(GrabacionService grabaciones, GrabacionTicketService tickets) {
        this.grabaciones = grabaciones;
        this.tickets = tickets;
    }

    // Una parte del video (cuerpo binario). n empieza en 0. Solo el mentor de la sesión.
    @PostMapping(value = "/sesiones/{id}/grabacion/parte", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Void> parte(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id, @RequestParam int n,
                                      @RequestBody byte[] datos) {
        grabaciones.agregarParte(mentor(jwt), id, n, datos);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sesiones/{id}/grabacion/terminar")
    public ResponseEntity<Void> terminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id, @RequestParam(defaultValue = "0") int duracion) {
        grabaciones.terminar(mentor(jwt), id, duracion);
        return ResponseEntity.noContent().build();
    }

    // La dirección para ver la grabación (la pide el estudiante o el mentor de la sesión).
    @PostMapping("/sesiones/{id}/grabacion/ticket")
    public ResponseEntity<Map<String, String>> ticket(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        grabaciones.archivoParaVer(Integer.valueOf(jwt.getSubject()), id);
        return ResponseEntity.ok(Map.of("url", "/api/grabaciones/ver/" + tickets.emitir(id)));
    }

    // El video, con soporte para avanzar y retroceder (Range). Sin token de sesión: entra con el ticket.
    @GetMapping("/grabaciones/ver/{ticket}")
    public ResponseEntity<Resource> ver(@PathVariable String ticket) {
        Integer sesionId = tickets.sesionDe(ticket)
                .orElseThrow(() -> new SesionException(HttpStatus.NOT_FOUND, "El enlace de la grabación venció."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/webm"))
                .body(new FileSystemResource(grabaciones.archivoLista(sesionId)));
    }

    private static Integer mentor(Jwt jwt) {
        if (!"MENTOR".equals(jwt.getClaimAsString("rol"))) {
            throw new SesionException(HttpStatus.FORBIDDEN, "Solo el mentor de la sesión puede grabarla.");
        }
        return Integer.valueOf(jwt.getSubject());
    }
}
