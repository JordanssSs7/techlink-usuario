package com.techlink.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// HU-04: limita cuantas veces por hora un usuario puede pedirle una recomendacion a la IA, para no agotar la cuota
// gratuita. Pasado el limite no hay error: el agente responde con el calculo base, que no cuesta nada.
@Component
public class LimitadorConsultas {

    private static final Duration VENTANA = Duration.ofHours(1);

    private final int maximoPorHora;
    private final Clock reloj;
    private final Map<Integer, Deque<Instant>> consultas = new ConcurrentHashMap<>();

    @Autowired
    public LimitadorConsultas(@Value("${app.agente.max-consultas-hora:10}") int maximoPorHora) {
        this(maximoPorHora, Clock.systemUTC());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    LimitadorConsultas(int maximoPorHora, Clock reloj) {
        this.maximoPorHora = maximoPorHora;
        this.reloj = reloj;
    }

    // Registra la consulta y devuelve true si el usuario aun tiene cupo en la ultima hora.
    public boolean intentar(Integer idUsuario) {
        Instant ahora = reloj.instant();
        Deque<Instant> usadas = consultas.computeIfAbsent(idUsuario, k -> new ArrayDeque<>());
        synchronized (usadas) {
            while (!usadas.isEmpty() && usadas.peekFirst().isBefore(ahora.minus(VENTANA))) {
                usadas.pollFirst();
            }
            if (usadas.size() >= maximoPorHora) {
                return false;
            }
            usadas.addLast(ahora);
            return true;
        }
    }
}
