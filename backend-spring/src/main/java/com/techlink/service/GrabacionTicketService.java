package com.techlink.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// El <video> del navegador no puede mandar el token de sesión, así que para ver una grabación se pide (con token) una dirección con un ticket
// que sirve unas horas para esa grabación y se puede usar varias veces (el video se pide por trozos al avanzar o retroceder).
@Service
public class GrabacionTicketService {

    static final long HORAS_DE_VIDA = 3;

    private record Guardado(Integer sesionId, Instant vence) {
    }

    private final Map<String, Guardado> tickets = new ConcurrentHashMap<>();
    private final SecureRandom azar = new SecureRandom();
    private final Clock reloj = Clock.systemUTC();

    public String emitir(Integer sesionId) {
        Instant ahora = reloj.instant();
        tickets.values().removeIf(g -> !g.vence().isAfter(ahora));
        byte[] bytes = new byte[24];
        azar.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Guardado(sesionId, ahora.plusSeconds(HORAS_DE_VIDA * 3600)));
        return ticket;
    }

    public Optional<Integer> sesionDe(String ticket) {
        Guardado g = ticket == null ? null : tickets.get(ticket);
        return g == null || !g.vence().isAfter(reloj.instant()) ? Optional.empty() : Optional.of(g.sesionId());
    }
}
