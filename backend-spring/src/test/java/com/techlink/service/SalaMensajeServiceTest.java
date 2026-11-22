package com.techlink.service;

import com.techlink.dto.SalaDtos.Mensaje;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.SalaMensaje;
import com.techlink.repository.SalaMensajeRepository;
import com.techlink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-08: mensajes del chat de la sala (texto y código), historial y límite de ritmo.
class SalaMensajeServiceTest {

    private static final SalaAccesoService.Sala SALA = new SalaAccesoService.Sala(SalaAccesoService.Tipo.SESION, 7, "Sesión",
            LocalDateTime.of(2026, 11, 20, 11, 0), LocalDateTime.of(2026, 11, 20, 14, 0), 2);

    private static final class RelojMovil extends Clock {
        Instant ahora = Instant.parse("2026-11-20T17:00:00Z");

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private SalaMensajeRepository repositorio;
    private UsuarioRepository usuarioRepository;
    private RelojMovil reloj;
    private SalaMensajeService servicio;

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(SalaMensajeRepository.class);
        usuarioRepository = Mockito.mock(UsuarioRepository.class);
        reloj = new RelojMovil();
        servicio = new SalaMensajeService(repositorio, usuarioRepository, reloj);
        when(repositorio.save(any(SalaMensaje.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Mensaje texto(String contenido) {
        return servicio.enviar(SALA, 10, "Ana Quispe", "TEXTO", null, contenido);
    }

    @Test
    void guardaUnMensajeDeTextoEnLaSalaDelUsuario() {
        Mensaje m = texto("  Hola, ¿empezamos?  ");

        ArgumentCaptor<SalaMensaje> guardado = ArgumentCaptor.forClass(SalaMensaje.class);
        verify(repositorio).save(guardado.capture());
        assertEquals("SESION", guardado.getValue().getSalaTipo());
        assertEquals(7, guardado.getValue().getSalaId());
        assertEquals(10, guardado.getValue().getIdUsuario());
        assertEquals("TEXTO", m.tipo());
        assertEquals("Hola, ¿empezamos?", m.contenido());
        assertNull(m.lenguaje());
        assertEquals("Ana Quispe", m.autor());
    }

    @Test
    void guardaUnFragmentoDeCodigoConSuLenguaje() {
        Mensaje m = servicio.enviar(SALA, 10, "Ana", "CODIGO", "Java", "int x = 1;\nSystem.out.println(x);");

        assertEquals("CODIGO", m.tipo());
        assertEquals("java", m.lenguaje());
        assertEquals("int x = 1;\nSystem.out.println(x);", m.contenido());
    }

    @Test
    void unLenguajeDesconocidoSeGuardaComoTexto() {
        assertEquals("texto", servicio.enviar(SALA, 10, "Ana", "CODIGO", "brainfuck", "+++").lenguaje());
        assertEquals("texto", servicio.enviar(SALA, 10, "Ana", "CODIGO", null, "+++").lenguaje());
    }

    @Test
    void enElCodigoSeConservaLaSangria() {
        Mensaje m = servicio.enviar(SALA, 10, "Ana", "CODIGO", "python", "\n\ndef f():\n    return 1   \n\n");

        assertEquals("def f():\n    return 1", m.contenido());
    }

    @Test
    void sinContenidoSeRechaza() {
        DatosInvalidosException texto = assertThrows(DatosInvalidosException.class, () -> texto("   "));
        DatosInvalidosException codigo = assertThrows(DatosInvalidosException.class,
                () -> servicio.enviar(SALA, 10, "Ana", "CODIGO", "java", "\n\n"));
        assertThrows(DatosInvalidosException.class, () -> texto(null));

        assertTrue(texto.getCampos().containsKey("contenido"));
        assertEquals("Escribe o pega el código.", codigo.getCampos().get("contenido"));
    }

    @Test
    void unTextoMuyLargoSeRechaza() {
        assertEquals(1000, SalaMensajeService.MAX_TEXTO);
        texto("a".repeat(1000));

        DatosInvalidosException error = assertThrows(DatosInvalidosException.class, () -> texto("a".repeat(1001)));

        assertTrue(error.getCampos().get("contenido").contains("1000"));
    }

    @Test
    void unCodigoAdmiteHasta4000Caracteres() {
        servicio.enviar(SALA, 10, "Ana", "CODIGO", "java", "a".repeat(4000));

        assertThrows(DatosInvalidosException.class, () -> servicio.enviar(SALA, 11, "Beto", "CODIGO", "java", "a".repeat(4001)));
    }

    @Test
    void sePuedeEscribirHastaDiezMensajesCadaDiezSegundos() {
        for (int i = 0; i < 10; i++) {
            texto("mensaje " + i);
        }

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> texto("uno más"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getEstado());
    }

    @Test
    void pasadaLaVentanaSePuedeVolverAEscribir() {
        for (int i = 0; i < 10; i++) {
            texto("mensaje " + i);
        }
        reloj.ahora = reloj.ahora.plus(Duration.ofSeconds(11));

        texto("ya pasó el tiempo");
    }

    @Test
    void elLimiteEsPorPersona() {
        for (int i = 0; i < 10; i++) {
            texto("mensaje " + i);
        }

        servicio.enviar(SALA, 20, "Beto", "TEXTO", null, "yo sí puedo");
    }

    @Test
    void elMensajeRechazadoPorRitmoNoSeGuarda() {
        for (int i = 0; i < 10; i++) {
            texto("mensaje " + i);
        }
        assertThrows(ReglaNegocioException.class, () -> texto("de más"));

        verify(repositorio, Mockito.times(10)).save(any());
    }

    @Test
    void losCaracteresDeControlSeQuitan() {
        assertEquals("Hola mundo", texto("Hola\u0000 mun\u0007do").contenido().replace("\u0000", ""));
        assertEquals("a\nb", SalaMensajeService.limpiar("a\r\nb\u0001", false));
        assertEquals("a\tb", SalaMensajeService.limpiar("a\tb", true));
    }

    @Test
    void elHistorialVieneEnOrdenYConElNombreDeCadaAutor() {
        SalaMensaje reciente = new SalaMensaje("SESION", 7, 20, "TEXTO", null, "segundo");
        SalaMensaje antiguo = new SalaMensaje("SESION", 7, 10, "CODIGO", "sql", "SELECT 1");
        // El repositorio entrega del más reciente al más antiguo.
        when(repositorio.recientes(any(), any(), any())).thenReturn(List.of(reciente, antiguo));
        when(usuarioRepository.findAllById(any())).thenReturn(List.of(
                PruebaSesiones.usuario(10, "Ana", "Quispe", "ana@tecsup.edu.pe"),
                PruebaSesiones.usuario(20, "Lucía", "-", "lucia@tecsup.edu.pe")));

        List<Mensaje> historial = servicio.historial(SALA);

        assertEquals(List.of("SELECT 1", "segundo"), historial.stream().map(Mensaje::contenido).toList());
        assertEquals("Ana Quispe", historial.get(0).autor());
        assertEquals("Lucía", historial.get(1).autor());
        assertEquals("sql", historial.get(0).lenguaje());
    }
}
