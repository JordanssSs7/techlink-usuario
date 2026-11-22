package com.techlink.controller;

import com.techlink.dto.MentorEstadisticasDtos.Constancia;
import com.techlink.dto.MentorEstadisticasDtos.Estadisticas;
import com.techlink.service.ConstanciaService;
import com.techlink.service.EstadisticasMentorService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// HU-28 y HU-27: estadísticas y constancia de horas del mentor. /api/mentor/** exige el rol MENTOR (SecurityConfig):
// 401 sin token, 403 con otro rol. 404 si el mentor todavía no creó su perfil.
@RestController
@RequestMapping("/api/mentor")
public class MentorEstadisticasController {

    private final EstadisticasMentorService estadisticasService;
    private final ConstanciaService constanciaService;

    public MentorEstadisticasController(EstadisticasMentorService estadisticasService, ConstanciaService constanciaService) {
        this.estadisticasService = estadisticasService;
        this.constanciaService = constanciaService;
    }

    // HU-28: sesiones, horas y promedio de estrellas por mes. ?meses= (1 a 24, 12 si no se indica) elige cuántos meses mostrar.
    @GetMapping("/estadisticas")
    public ResponseEntity<Estadisticas> estadisticas(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam(required = false) Integer meses) {
        return ResponseEntity.ok(estadisticasService.estadisticas(Integer.valueOf(jwt.getSubject()), meses));
    }

    // HU-27: los datos que llevará la constancia (horas totales y cursos atendidos), para mostrarlos antes de descargarla.
    @GetMapping("/constancia")
    public ResponseEntity<Constancia> constancia(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(constanciaService.datos(Integer.valueOf(jwt.getSubject())));
    }

    // HU-27: el PDF. Con ?descargar=true se baja como archivo; sin él se abre en pantalla (vista previa).
    // 409 si todavía no tiene sesiones completadas.
    @GetMapping("/constancia/pdf")
    public ResponseEntity<byte[]> constanciaPdf(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(defaultValue = "false") boolean descargar) {
        byte[] pdf = constanciaService.pdf(Integer.valueOf(jwt.getSubject()));
        ContentDisposition disposicion = (descargar ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename("constancia-mentoria-techlink.pdf").build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(pdf);
    }
}
