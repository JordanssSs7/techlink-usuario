package com.techlink.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pruebas del limitador de consultas a la IA (HU-04), con un reloj que se mueve a mano.
class LimitadorConsultasTest {

    // Reloj de prueba: el tiempo solo avanza cuando la prueba lo pide.
    private static class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-11-04T10:00:00Z");

        void avanzar(Duration d) { ahora = ahora.plus(d); }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    @Test
    void permiteHastaElMaximoYLuegoNiega() {
        LimitadorConsultas limitador = new LimitadorConsultas(3, new RelojManual());

        assertTrue(limitador.intentar(1));
        assertTrue(limitador.intentar(1));
        assertTrue(limitador.intentar(1));
        assertFalse(limitador.intentar(1));
    }

    @Test
    void cadaUsuarioTieneSuPropioCupo() {
        LimitadorConsultas limitador = new LimitadorConsultas(1, new RelojManual());

        assertTrue(limitador.intentar(1));
        assertFalse(limitador.intentar(1));
        assertTrue(limitador.intentar(2));
    }

    @Test
    void elCupoSeRecuperaCuandoPasaUnaHora() {
        RelojManual reloj = new RelojManual();
        LimitadorConsultas limitador = new LimitadorConsultas(1, reloj);

        assertTrue(limitador.intentar(1));
        reloj.avanzar(Duration.ofMinutes(59));
        assertFalse(limitador.intentar(1));
        reloj.avanzar(Duration.ofMinutes(2));
        assertTrue(limitador.intentar(1));
    }

    @Test
    void unaConsultaNegadaNoConsumeCupoNuevo() {
        RelojManual reloj = new RelojManual();
        LimitadorConsultas limitador = new LimitadorConsultas(1, reloj);

        assertTrue(limitador.intentar(1));
        reloj.avanzar(Duration.ofMinutes(30));
        assertFalse(limitador.intentar(1));
        assertFalse(limitador.intentar(1));
        // Pasan 31 minutos mas: la primera consulta ya salio de la ventana aunque se insistio varias veces.
        reloj.avanzar(Duration.ofMinutes(31));
        assertTrue(limitador.intentar(1));
    }
}
