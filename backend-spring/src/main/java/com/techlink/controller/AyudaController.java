package com.techlink.controller;

import com.techlink.dto.AyudaDtos.AyudaResponse;
import com.techlink.service.AyudaService;
import com.techlink.service.SoporteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// HU-30: centro de ayuda con preguntas frecuentes. Requiere el token JWT (cualquier rol).
@RestController
@RequestMapping("/api/ayuda")
public class AyudaController {

    private final AyudaService ayudaService;
    private final SoporteService soporteService;

    public AyudaController(AyudaService ayudaService, SoporteService soporteService) {
        this.ayudaService = ayudaService;
        this.soporteService = soporteService;
    }

    // Escribir a soporte desde la página. Body: { "asunto": "...", "mensaje": "..." }. 400 con "campos" si falta algo, 429 si abusa.
    @PostMapping("/contacto")
    public ResponseEntity<Void> contactar(@AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, String> cuerpo) {
        soporteService.enviar(Integer.valueOf(jwt.getSubject()), cuerpo.get("asunto"), cuerpo.get("mensaje"));
        return ResponseEntity.noContent().build();
    }

    // Las preguntas agrupadas por tema. ?q= las filtra por palabra clave.
    @GetMapping
    public ResponseEntity<AyudaResponse> consultar(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(ayudaService.consultar(q));
    }
}
