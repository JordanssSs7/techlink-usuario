package com.techlink.controller;

import com.techlink.dto.SalaDtos.SalaInfo;
import com.techlink.dto.SalaDtos.ServidorIce;
import com.techlink.dto.SalaDtos.Ticket;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.service.SalaAccesoService;
import com.techlink.service.SalaTicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// El invitado de una charla entra a su sala con el enlace secreto que le manda el administrador, sin cuenta ni contraseña: el enlace es
// su permiso. No requiere sesión (SecurityConfig lo deja abierto), pero sin el enlace correcto no hay manera de entrar.
//  - GET  /api/charlas/invitado/{token}: datos de la charla y si ya se puede entrar.
//  - POST /api/charlas/invitado/{token}/ticket: ticket de un solo uso para conectarse a /ws/sala.
@RestController
@RequestMapping("/api/charlas/invitado/{token}")
public class CharlaInvitadoController {

    public record Entrada(String nombre) {
    }

    private final SalaAccesoService acceso;
    private final SalaTicketService tickets;
    private final String[] servidoresStun;
    private final String turnUrl;
    private final String turnUsuario;
    private final String turnClave;

    public CharlaInvitadoController(SalaAccesoService acceso, SalaTicketService tickets,
                                    @Value("${app.sala.stun:stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302}") String[] servidoresStun,
                                    @Value("${app.sala.turn-url:}") String turnUrl,
                                    @Value("${app.sala.turn-usuario:}") String turnUsuario,
                                    @Value("${app.sala.turn-clave:}") String turnClave) {
        this.acceso = acceso;
        this.tickets = tickets;
        this.servidoresStun = servidoresStun;
        this.turnUrl = turnUrl;
        this.turnUsuario = turnUsuario;
        this.turnClave = turnClave;
    }

    @GetMapping
    public ResponseEntity<SalaInfo> info(@PathVariable String token) {
        SalaAccesoService.Sala sala = acceso.autorizarInvitado(token);
        LocalDateTime ahora = acceso.ahora();
        return ResponseEntity.ok(new SalaInfo(sala.tipo().name(), sala.id(), sala.titulo(), sala.abre().toString(),
                sala.cierra().toString(), sala.abiertaEn(ahora), sala.motivoCerrada(ahora), sala.maxParticipantes()));
    }

    @PostMapping("/ticket")
    public ResponseEntity<Ticket> ticket(@PathVariable String token, @RequestBody(required = false) Entrada entrada) {
        SalaAccesoService.Sala sala = acceso.autorizarInvitado(token);
        String motivo = sala.motivoCerrada(acceso.ahora());
        if (motivo != null) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT, motivo);
        }
        String nombre = entrada == null || entrada.nombre() == null ? "" : entrada.nombre().trim().replaceAll("\\s+", " ");
        if (nombre.length() < 2 || nombre.length() > 60) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Escribe tu nombre (entre 2 y 60 letras).");
        }
        // El invitado no tiene cuenta: su id en la sala es el de la charla en negativo, que nunca choca con un usuario real.
        String codigo = tickets.emitir(new SalaTicketService.Datos(-sala.id(), nombre, SalaAccesoService.ROL_INVITADO,
                sala.tipo(), sala.id()));
        return ResponseEntity.ok(new Ticket(codigo, SalaTicketService.SEGUNDOS_DE_VIDA, servidoresIce()));
    }

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
}
