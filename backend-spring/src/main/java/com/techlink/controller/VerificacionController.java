package com.techlink.controller;

import com.techlink.dto.MentorEstadisticasDtos.Verificacion;
import com.techlink.service.ConstanciaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// HU-27: verificación pública de constancias. No pide token: cualquiera que reciba una constancia puede escanear su código QR (o
// escribir su código) y comprobar que la emitió TechLink. Responde 200 siempre; "valida" dice si el código existe.
@RestController
@RequestMapping("/api/verificar")
public class VerificacionController {

    private final ConstanciaService constanciaService;

    public VerificacionController(ConstanciaService constanciaService) {
        this.constanciaService = constanciaService;
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<Verificacion> verificar(@PathVariable String codigo) {
        return ResponseEntity.ok(constanciaService.verificar(codigo));
    }
}
