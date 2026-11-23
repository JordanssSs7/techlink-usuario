package com.techlink.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tiempo real general: quién está en línea y a quién se le avisa.
class EventosRegistroTest {

    private static final class Falso implements SalaRegistro.Canal {
        final List<String> recibidos = new ArrayList<>();

        @Override public void enviar(String json) { recibidos.add(json); }
        @Override public void cerrar(int codigo, String motivo) { }
    }

    @Test
    void estaEnLineaMientrasQuedeUnaPestana() {
        EventosRegistro registro = new EventosRegistro();
        Falso a = new Falso();
        Falso b = new Falso();

        assertTrue(registro.conectar(7, a));
        assertFalse(registro.conectar(7, b));
        assertFalse(registro.desconectar(7, a));
        assertTrue(registro.estaEnLinea(7));
        assertTrue(registro.desconectar(7, b));
        assertFalse(registro.estaEnLinea(7));
    }

    @Test
    void elAvisoLlegaSoloAEsaPersonaYLaDifusionATodos() {
        EventosRegistro registro = new EventosRegistro();
        Falso ana = new Falso();
        Falso beto = new Falso();
        registro.conectar(1, ana);
        registro.conectar(2, beto);

        registro.enviarA(1, "{\"t\":\"notificacion\"}");
        registro.difundir("{\"t\":\"foto\"}");

        assertEquals(2, ana.recibidos.size());
        assertEquals(1, beto.recibidos.size());
        assertEquals(java.util.Set.of(1, 2), registro.enLinea());
    }

    @Test
    void unaConexionCaidaNoImpideAvisarALasDemas() {
        EventosRegistro registro = new EventosRegistro();
        registro.conectar(1, new SalaRegistro.Canal() {
            @Override public void enviar(String json) { throw new IllegalStateException("caída"); }
            @Override public void cerrar(int codigo, String motivo) { }
        });
        Falso beto = new Falso();
        registro.conectar(2, beto);

        registro.difundir("{}");

        assertEquals(1, beto.recibidos.size());
    }
}
