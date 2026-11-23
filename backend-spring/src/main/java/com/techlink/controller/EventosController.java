package com.techlink.controller;

import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Usuario;
import com.techlink.repository.UsuarioRepository;
import com.techlink.service.SalaMensajeService;
import com.techlink.service.SalaTicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// POST /api/eventos/ticket: ticket de un solo uso para conectarse a /ws/eventos?ticket=... (tiempo real: en línea, avisos, fotos).
@RestController
@RequestMapping("/api/eventos")
public class EventosController {

    private final SalaTicketService tickets;
    private final UsuarioRepository usuarioRepository;

    public EventosController(SalaTicketService tickets, UsuarioRepository usuarioRepository) {
        this.tickets = tickets;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/ticket")
    public ResponseEntity<Map<String, Object>> ticket(@AuthenticationPrincipal Jwt jwt) {
        Integer usuarioId = Integer.valueOf(jwt.getSubject());
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.FORBIDDEN, "La cuenta no existe."));
        String codigo = tickets.emitir(new SalaTicketService.Datos(usuarioId, SalaMensajeService.nombreDe(usuario),
                jwt.getClaimAsString("rol"), null, null));
        return ResponseEntity.ok(Map.of("ticket", codigo, "expiraSegundos", SalaTicketService.SEGUNDOS_DE_VIDA));
    }
}
