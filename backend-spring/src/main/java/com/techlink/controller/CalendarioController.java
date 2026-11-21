package com.techlink.controller;

import com.techlink.dto.EventoCalendarioResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.service.CalendarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

// HU-26: calendario con las sesiones y las charlas del usuario. Requiere el token de un ESTUDIANTE o MENTOR.
// ?desde=AAAA-MM-DD&hasta=AAAA-MM-DD (máximo 62 días). 400 si las fechas faltan o no son válidas.
@RestController
@RequestMapping("/api/calendario")
public class CalendarioController {

    private final CalendarioService calendarioService;

    public CalendarioController(CalendarioService calendarioService) {
        this.calendarioService = calendarioService;
    }

    @GetMapping
    public ResponseEntity<List<EventoCalendarioResponse>> eventos(@AuthenticationPrincipal Jwt jwt,
                                                                  @RequestParam(required = false) String desde,
                                                                  @RequestParam(required = false) String hasta) {
        String rol = jwt.getClaimAsString("rol");
        if (!"ESTUDIANTE".equals(rol) && !"MENTOR".equals(rol)) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "El calendario es para estudiantes y mentores.");
        }
        return ResponseEntity.ok(calendarioService.eventos(Integer.valueOf(jwt.getSubject()), rol, fecha(desde), fecha(hasta)));
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
