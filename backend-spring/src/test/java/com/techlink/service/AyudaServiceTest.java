package com.techlink.service;

import com.techlink.dto.AyudaDtos.AyudaResponse;
import com.techlink.model.AyudaPregunta;
import com.techlink.model.AyudaTema;
import com.techlink.repository.AyudaPreguntaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

// Pruebas de HU-30: centro de ayuda con preguntas por tema y búsqueda por palabra clave.
class AyudaServiceTest {

    private AyudaService servicio;

    private static AyudaTema tema(int id, String nombre, int orden) {
        AyudaTema t = new AyudaTema();
        t.setId(id);
        t.setNombre(nombre);
        t.setOrden(orden);
        return t;
    }

    private static AyudaPregunta pregunta(int id, AyudaTema tema, String pregunta, String respuesta) {
        AyudaPregunta p = new AyudaPregunta();
        p.setId(id);
        p.setTema(tema);
        p.setPregunta(pregunta);
        p.setRespuesta(respuesta);
        return p;
    }

    @BeforeEach
    void preparar() {
        AyudaPreguntaRepository repositorio = Mockito.mock(AyudaPreguntaRepository.class);
        AyudaTema cuenta = tema(1, "Cuenta y acceso", 1);
        AyudaTema sesiones = tema(2, "Mentorías y sesiones", 2);
        when(repositorio.activas()).thenReturn(List.of(
                pregunta(1, cuenta, "¿Cómo inicio sesión en TechLink?", "Con tu cuenta de Google institucional."),
                pregunta(2, cuenta, "¿Cómo cambio mi foto?", "Entra a Mi cuenta y sube una imagen."),
                pregunta(3, sesiones, "¿Puedo cancelar una sesión?", "Sí, hasta 2 horas antes."),
                pregunta(4, sesiones, "¿Cómo califico?", "Al terminar la sesión pulsa Calificar.")));
        servicio = new AyudaService(repositorio, "soporte@techlink.edu.pe");
    }

    @Test
    void sinBusquedaListaTodasAgrupadasPorTemaEnOrden() {
        AyudaResponse r = servicio.consultar(null);

        assertEquals(4, r.total());
        assertEquals(List.of("Cuenta y acceso", "Mentorías y sesiones"), r.temas().stream().map(t -> t.nombre()).toList());
        assertEquals(2, r.temas().get(0).preguntas().size());
    }

    @Test
    void laBusquedaIgnoraMayusculasYTildes() {
        assertEquals(3, servicio.consultar("SESION").total());   // «sesión» en una pregunta y «sesiones» en el tema de otras dos
        assertEquals(1, servicio.consultar("como inicio").total());
        assertEquals(1, servicio.consultar("Cómo CAMBIO").total());
    }

    @Test
    void conVariasPalabrasDebenEstarTodas() {
        AyudaResponse r = servicio.consultar("cancelar sesion");

        assertEquals(1, r.total());
        assertEquals("¿Puedo cancelar una sesión?", r.temas().get(0).preguntas().get(0).pregunta());
    }

    @Test
    void tambienBuscaEnLaRespuestaYEnElTema() {
        assertEquals(1, servicio.consultar("imagen").total());      // solo en la respuesta
        assertEquals(2, servicio.consultar("mentorias").total());   // en el nombre del tema
    }

    @Test
    void soloQuedanLosTemasConResultados() {
        AyudaResponse r = servicio.consultar("foto");

        assertEquals(1, r.temas().size());
        assertEquals("Cuenta y acceso", r.temas().get(0).nombre());
    }

    @Test
    void sinResultadosDevuelveListaVaciaYElCorreoDeSoporte() {
        AyudaResponse r = servicio.consultar("zzzxxx");

        assertEquals(0, r.total());
        assertTrue(r.temas().isEmpty());
        assertEquals("soporte@techlink.edu.pe", r.correoSoporte());
        assertEquals("zzzxxx", r.busqueda());
    }

    @Test
    void siempreDevuelveElCorreoDeSoporte() {
        assertEquals("soporte@techlink.edu.pe", servicio.consultar(null).correoSoporte());
    }

    @Test
    void lasPalabrasDeUnaSolaLetraSeIgnoran() {
        assertEquals(4, servicio.consultar("a").total());
        assertEquals(List.of("cancelar"), AyudaService.palabras("¿Puedo, a, cancelar?").subList(1, 2));
    }

    @Test
    void unaBusquedaMuyLargaSeRecorta() {
        assertEquals(AyudaService.MAX_BUSQUEDA, servicio.consultar("a".repeat(500)).busqueda().length());
    }

    @Test
    void normalizarQuitaTildesYMinusculas() {
        assertEquals("sesion de mentoria", AyudaService.normalizar("SESIÓN de Mentoría"));
    }
}
