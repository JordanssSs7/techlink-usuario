package com.techlink.service;

import com.techlink.service.SalaRegistro.Canal;
import com.techlink.service.SalaRegistro.Estado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pruebas de HU-08: quién está conectado en cada sala virtual.
class SalaRegistroTest {

    // Un canal falso que guarda lo que se le envía.
    private static final class CanalFalso implements Canal {
        final List<String> recibidos = new ArrayList<>();
        int codigoDeCierre = -1;
        boolean falla;

        @Override
        public void enviar(String json) {
            if (falla) {
                throw new IllegalStateException("conexión caída");
            }
            recibidos.add(json);
        }

        @Override
        public void cerrar(int codigo, String motivo) {
            codigoDeCierre = codigo;
        }
    }

    private SalaRegistro registro;
    private CanalFalso ana;
    private CanalFalso lucia;

    @BeforeEach
    void preparar() {
        registro = new SalaRegistro();
        ana = new CanalFalso();
        lucia = new CanalFalso();
    }

    private SalaRegistro.Unido unir(String sala, int id, String nombre, Canal canal, int maximo) {
        return assertInstanceOf(SalaRegistro.Unido.class, registro.unir(sala, id, nombre, "ESTUDIANTE", canal, maximo));
    }

    @Test
    void elPrimeroEntraSinOtrosYElSegundoVeAlPrimero() {
        assertTrue(unir("SESION-1", 10, "Ana", ana, 2).otros().isEmpty());

        SalaRegistro.Unido segundo = unir("SESION-1", 20, "Lucía", lucia, 2);

        assertEquals(1, segundo.otros().size());
        assertEquals("Ana", segundo.otros().get(0).nombre());
        assertEquals(2, registro.participantes("SESION-1").size());
    }

    @Test
    void lasSalasEstanSeparadas() {
        unir("SESION-1", 10, "Ana", ana, 2);
        unir("SESION-2", 20, "Lucía", lucia, 2);

        assertEquals(1, registro.participantes("SESION-1").size());
        assertTrue(registro.participante("SESION-1", 20).isEmpty());
    }

    @Test
    void conLaSalaLlenaNoEntraUnTercero() {
        unir("SESION-1", 10, "Ana", ana, 2);
        unir("SESION-1", 20, "Lucía", lucia, 2);

        assertInstanceOf(SalaRegistro.Llena.class, registro.unir("SESION-1", 30, "Beto", "ESTUDIANTE", new CanalFalso(), 2));
        assertEquals(2, registro.participantes("SESION-1").size());
    }

    @Test
    void quienYaEstabaPuedeVolverAEntrarAunqueLaSalaEsteLlena() {
        unir("SESION-1", 10, "Ana", ana, 2);
        unir("SESION-1", 20, "Lucía", lucia, 2);

        unir("SESION-1", 10, "Ana", new CanalFalso(), 2);

        assertEquals(2, registro.participantes("SESION-1").size());
    }

    @Test
    void otraPestanaCierraLaAnteriorYLaReemplaza() {
        unir("SESION-1", 10, "Ana", ana, 2);
        CanalFalso nueva = new CanalFalso();

        unir("SESION-1", 10, "Ana", nueva, 2);

        assertEquals(SalaRegistro.CERRADA_POR_OTRA_PESTANA, ana.codigoDeCierre);
        assertEquals(nueva, registro.participante("SESION-1", 10).orElseThrow().canal());
    }

    @Test
    void elCierreDeLaPestanaViejaNoSacaALaNueva() {
        unir("SESION-1", 10, "Ana", ana, 2);
        CanalFalso nueva = new CanalFalso();
        unir("SESION-1", 10, "Ana", nueva, 2);

        assertFalse(registro.salir("SESION-1", 10, ana));
        assertTrue(registro.participante("SESION-1", 10).isPresent());
        assertTrue(registro.salir("SESION-1", 10, nueva));
        assertTrue(registro.participante("SESION-1", 10).isEmpty());
    }

    @Test
    void alSalirLaUltimaPersonaLaSalaSeVacia() {
        unir("SESION-1", 10, "Ana", ana, 2);

        assertTrue(registro.salir("SESION-1", 10, ana));

        assertTrue(registro.participantes("SESION-1").isEmpty());
        assertFalse(registro.salir("SESION-1", 10, ana));
    }

    @Test
    void difundirLlegaATodosMenosAlExcluido() {
        unir("SESION-1", 10, "Ana", ana, 2);
        unir("SESION-1", 20, "Lucía", lucia, 2);

        registro.difundir("SESION-1", "{\"t\":\"hola\"}", 10);

        assertTrue(ana.recibidos.isEmpty());
        assertEquals(List.of("{\"t\":\"hola\"}"), lucia.recibidos);
    }

    @Test
    void difundirSinExcluirLlegaATodos() {
        unir("SESION-1", 10, "Ana", ana, 2);
        unir("SESION-1", 20, "Lucía", lucia, 2);

        registro.difundir("SESION-1", "x", null);

        assertEquals(1, ana.recibidos.size());
        assertEquals(1, lucia.recibidos.size());
    }

    @Test
    void unaConexionCaidaNoImpideQueLleguenLosDemas() {
        unir("SESION-1", 10, "Ana", ana, 2);
        unir("SESION-1", 20, "Lucía", lucia, 2);
        ana.falla = true;

        registro.difundir("SESION-1", "x", null);

        assertEquals(1, lucia.recibidos.size());
    }

    @Test
    void enviarAUnaPersonaSoloLeLlegaAEsaPersona() {
        unir("SESION-1", 10, "Ana", ana, 2);
        unir("SESION-1", 20, "Lucía", lucia, 2);

        assertTrue(registro.enviarA("SESION-1", 20, "señal"));
        assertFalse(registro.enviarA("SESION-1", 99, "señal"));

        assertTrue(ana.recibidos.isEmpty());
        assertEquals(List.of("señal"), lucia.recibidos);
    }

    @Test
    void elEstadoDeCamaraYPantallaSeGuarda() {
        unir("SESION-1", 10, "Ana", ana, 2);
        assertEquals(Estado.INICIAL, registro.participante("SESION-1", 10).orElseThrow().estado());

        registro.actualizarEstado("SESION-1", 10, new Estado(true, false, true, "stream-1"));

        Estado estado = registro.participante("SESION-1", 10).orElseThrow().estado();
        assertTrue(estado.camara());
        assertFalse(estado.microfono());
        assertEquals("stream-1", estado.pantallaStream());
        assertTrue(registro.actualizarEstado("SESION-1", 99, Estado.INICIAL).isEmpty());
    }

    @Test
    void elNuevoVeElEstadoActualDeLosQueYaEstaban() {
        unir("SESION-1", 10, "Ana", ana, 3);
        registro.actualizarEstado("SESION-1", 10, new Estado(true, true, false, null));

        SalaRegistro.Unido segundo = unir("SESION-1", 20, "Lucía", lucia, 3);

        assertTrue(segundo.otros().get(0).estado().camara());
    }
}
