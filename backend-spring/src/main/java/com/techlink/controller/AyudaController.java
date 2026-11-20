package com.techlink.controller;

import com.techlink.dto.AyudaDtos.AyudaResponse;
import com.techlink.service.AyudaService;
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

    public AyudaController(AyudaService ayudaService) {
        this.ayudaService = ayudaService;
    }

    // Las preguntas agrupadas por tema. ?q= las filtra por palabra clave.
    @GetMapping
    public ResponseEntity<AyudaResponse> consultar(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(ayudaService.consultar(q));
    }
}
