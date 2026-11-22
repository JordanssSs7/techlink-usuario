package com.techlink.controller;

import com.techlink.dto.SalaDtos.SalaInfo;
import com.techlink.dto.SalaDtos.ServidorIce;
import com.techlink.dto.SalaDtos.Ticket;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Usuario;
import com.techlink.repository.UsuarioRepository;
import com.techlink.service.SalaAccesoService;
import com.techlink.service.SalaMensajeService;
import com.techlink.service.SalaTicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// HU-08: salas virtuales (videollamada, pantalla compartida y chat con código) de las sesiones de mentoría y de las charlas.
// Requiere el token JWT de un ESTUDIANTE o un MENTOR (403 con otro rol). {tipo} es "sesion" o "charla".
//  - GET /api/salas/{tipo}/{id}: datos de la sala y si se puede entrar ahora.
//  - POST /api/salas/{tipo}/{id}/ticket: ticket de un solo uso para conectarse a /ws/sala?ticket=...
// 403 si no participa en esa sesión (o no está inscrito en la charla), 404 si no existe, 409 si se canceló o está
// fuera del horario de la sala.
@RestController
@RequestMapping("/api/salas")
public class SalaController {

    private final SalaAccesoService acceso;
    private final SalaTicketService tickets;
    private final UsuarioRepository usuarioRepository;
    private final String[] servidoresStun;
    private final String turnUrl;
    private final String turnUsuario;
    private final String turnClave;

    public SalaController(SalaAccesoService acceso, SalaTicketService tickets, UsuarioRepository usuarioRepository,
                          @Value("${app.sala.stun:stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302}") String[] servidoresStun,
                          @Value("${app.sala.turn-url:}") String turnUrl,
                          @Value("${app.sala.turn-usuario:}") String turnUsuario,
                          @Value("${app.sala.turn-clave:}") String turnClave) {
        this.acceso = acceso;
        this.tickets = tickets;
        this.usuarioRepository = usuarioRepository;
        this.servidoresStun = servidoresStun;
        this.turnUrl = turnUrl;
        this.turnUsuario = turnUsuario;
        this.turnClave = turnClave;
    }

    @GetMapping("/{tipo}/{id}")
    public ResponseEntity<SalaInfo> info(@AuthenticationPrincipal Jwt jwt, @PathVariable String tipo, @PathVariable Integer id) {
        SalaAccesoService.Sala sala = acceso.autorizar(usuarioDe(jwt), SalaAccesoService.Tipo.de(tipo), id);
        LocalDateTime ahora = acceso.ahora();
        return ResponseEntity.ok(new SalaInfo(sala.tipo().name(), sala.id(), sala.titulo(), sala.abre().toString(),
                sala.cierra().toString(), sala.abiertaEn(ahora), sala.motivoCerrada(ahora), sala.maxParticipantes()));
    }

    @PostMapping("/{tipo}/{id}/ticket")
    public ResponseEntity<Ticket> ticket(@AuthenticationPrincipal Jwt jwt, @PathVariable String tipo, @PathVariable Integer id) {
        Integer usuarioId = usuarioDe(jwt);
        SalaAccesoService.Sala sala = acceso.autorizar(usuarioId, SalaAccesoService.Tipo.de(tipo), id);
        String motivo = sala.motivoCerrada(acceso.ahora());
        if (motivo != null) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT, motivo);
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.FORBIDDEN, "La cuenta no existe."));
        String codigo = tickets.emitir(new SalaTicketService.Datos(usuarioId, SalaMensajeService.nombreDe(usuario),
                jwt.getClaimAsString("rol"), sala.tipo(), sala.id()));
        return ResponseEntity.ok(new Ticket(codigo, SalaTicketService.SEGUNDOS_DE_VIDA, servidoresIce()));
    }

    // STUN para descubrir la dirección pública de cada navegador; TURN (si se configuró) como respaldo cuando una red no
    // permite la conexión directa.
    private List<ServidorIce> servidoresIce() {
        List<ServidorIce> lista = new ArrayList<>();
        List<String> stun = java.util.Arrays.stream(servidoresStun).map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (!stun.isEmpty()) {
            lista.add(new ServidorIce(stun, null, null));
        }
        if (!turnUrl.isBlank()) {
            lista.add(new ServidorIce(List.of(turnUrl.trim()), turnUsuario, turnClave));
        }
        return lista;
    }

    private static Integer usuarioDe(Jwt jwt) {
        String rol = jwt.getClaimAsString("rol");
        if (!"ESTUDIANTE".equals(rol) && !"MENTOR".equals(rol)) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Solo estudiantes y mentores usan las salas virtuales.");
        }
        return Integer.valueOf(jwt.getSubject());
    }
}
