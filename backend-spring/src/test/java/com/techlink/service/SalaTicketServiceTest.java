package com.techlink.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pruebas de HU-08: el ticket de un solo uso con el que se abre la conexión de la sala.
class SalaTicketServiceTest {

    // Un reloj que se puede adelantar.
    private static final class RelojMovil extends Clock {
        Instant ahora = Instant.parse("2026-11-20T17:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private static final SalaTicketService.Datos DATOS =
            new SalaTicketService.Datos(10, "Ana Quispe", "ESTUDIANTE", SalaAccesoService.Tipo.SESION, 7);

    @Test
    void elTicketDevuelveQuienEntraYAQueSala() {
        SalaTicketService servicio = new SalaTicketService(new RelojMovil());

        Optional<SalaTicketService.Datos> datos = servicio.consumir(servicio.emitir(DATOS));

        assertEquals(DATOS, datos.orElseThrow());
    }

    @Test
    void sirveUnaSolaVez() {
        SalaTicketService servicio = new SalaTicketService(new RelojMovil());
        String ticket = servicio.emitir(DATOS);

        assertTrue(servicio.consumir(ticket).isPresent());
        assertTrue(servicio.consumir(ticket).isEmpty());
    }

    @Test
    void venceALos30Segundos() {
        RelojMovil reloj = new RelojMovil();
        SalaTicketService servicio = new SalaTicketService(reloj);
        String ticket = servicio.emitir(DATOS);

        reloj.ahora = reloj.ahora.plus(Duration.ofSeconds(SalaTicketService.SEGUNDOS_DE_VIDA));

        assertTrue(servicio.consumir(ticket).isEmpty());
    }

    @Test
    void justoAntesDeVencerTodaviaSirve() {
        RelojMovil reloj = new RelojMovil();
        SalaTicketService servicio = new SalaTicketService(reloj);
        String ticket = servicio.emitir(DATOS);

        reloj.ahora = reloj.ahora.plus(Duration.ofSeconds(SalaTicketService.SEGUNDOS_DE_VIDA - 1));

        assertTrue(servicio.consumir(ticket).isPresent());
    }

    @Test
    void unTicketInventadoONuloNoSirve() {
        SalaTicketService servicio = new SalaTicketService(new RelojMovil());
        servicio.emitir(DATOS);

        assertTrue(servicio.consumir("inventado").isEmpty());
        assertTrue(servicio.consumir(null).isEmpty());
    }

    @Test
    void cadaTicketEsDistintoYNoSePuedeAdivinar() {
        SalaTicketService servicio = new SalaTicketService(new RelojMovil());

        String a = servicio.emitir(DATOS);
        String b = servicio.emitir(DATOS);

        assertNotEquals(a, b);
        assertTrue(a.length() >= 40, "es largo (256 bits)");
    }
}
