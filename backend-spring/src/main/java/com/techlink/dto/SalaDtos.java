package com.techlink.dto;

import java.util.List;

// HU-08: lo que el frontend sabe de una sala virtual antes de entrar, y los mensajes del chat.
public final class SalaDtos {

    private SalaDtos() {
    }

    // puedeEntrar es false fuera del horario de la sala; "motivo" explica por qué (y cuándo abre). Las horas van en ISO.
    public record SalaInfo(String tipo, Integer id, String titulo, String abre, String cierra, boolean puedeEntrar,
                           String motivo, int maxParticipantes) {
    }

    // ice: servidores STUN/TURN para la videollamada (WebRTC). ticket: de un solo uso, vale unos segundos.
    public record Ticket(String ticket, int expiraSegundos, List<ServidorIce> ice) {
    }

    public record ServidorIce(List<String> urls, String username, String credential) {
    }

    // Un mensaje del chat. tipo: TEXTO o CODIGO (con su lenguaje). fecha en ISO.
    public record Mensaje(Long id, Integer autorId, String autor, String tipo, String lenguaje, String contenido, String fecha) {
    }
}
