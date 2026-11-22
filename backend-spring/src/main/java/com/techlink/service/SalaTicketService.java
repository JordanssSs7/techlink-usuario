package com.techlink.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// HU-08: el navegador no puede poner el token de sesión en una conexión WebSocket, y ponerlo en la dirección lo dejaría
// en los registros. Por eso, antes de conectar, el frontend pide (con su token) un ticket de un solo uso que vale unos
// segundos, y conecta con él. El ticket guarda quién es el usuario y a qué sala entra.
@Service
public class SalaTicketService {

    public static final int SEGUNDOS_DE_VIDA = 30;

    // Quién entra y a dónde. Se arma al emitir el ticket, cuando ya se comprobó que puede entrar.
    public record Datos(Integer usuarioId, String nombre, String rol, SalaAccesoService.Tipo tipo, Integer salaId) {
    }

    private record Guardado(Datos datos, Instant vence) {
    }

    private final Map<String, Guardado> tickets = new ConcurrentHashMap<>();
    private final SecureRandom azar = new SecureRandom();
    private final Clock reloj;

    @Autowired
    public SalaTicketService() {
        this(Clock.systemUTC());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    SalaTicketService(Clock reloj) {
        this.reloj = reloj;
    }

    public String emitir(Datos datos) {
        Instant ahora = reloj.instant();
        tickets.values().removeIf(g -> !g.vence().isAfter(ahora)); // limpia los vencidos
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(ticket, new Guardado(datos, ahora.plusSeconds(SEGUNDOS_DE_VIDA)));
        return ticket;
    }

    // Canjea el ticket: sirve una sola vez y solo mientras no venza.
    public Optional<Datos> consumir(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        Guardado guardado = tickets.remove(ticket);
        if (guardado == null || !guardado.vence().isAfter(reloj.instant())) {
            return Optional.empty();
        }
        return Optional.of(guardado.datos());
    }
}
