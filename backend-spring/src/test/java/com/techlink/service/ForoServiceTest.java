package com.techlink.service;

import com.techlink.dto.ForoDtos.ConsultaDetalle;
import com.techlink.dto.ForoDtos.ConsultaResumen;
import com.techlink.dto.ForoDtos.NuevaConsulta;
import com.techlink.dto.ForoDtos.NuevaRespuesta;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Consulta;
import com.techlink.model.Curso;
import com.techlink.model.Respuesta;
import com.techlink.model.Rol;
import com.techlink.model.Usuario;
import com.techlink.repository.ConsultaRepository;
import com.techlink.repository.CursoRepository;
import com.techlink.repository.RespuestaRepository;
import com.techlink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-17: foro de consultas por curso, respuestas y respuesta útil.
class ForoServiceTest {

    private static final int AUTOR = 1;       // quien hizo la consulta
    private static final int OTRO = 2;        // quien responde

    private ConsultaRepository consultaRepository;
    private RespuestaRepository respuestaRepository;
    private CursoRepository cursoRepository;
    private UsuarioRepository usuarioRepository;
    private ForoService servicio;
    private Consulta consulta;
    private final List<Respuesta> respuestas = new ArrayList<>();

    private static Usuario usuario(int id, String nombres, String apellidos, String rol) {
        Usuario u = PruebaSesiones.usuario(id, nombres, apellidos, "u" + id + "@tecsup.edu.pe");
        Rol r = new Rol();
        r.setNombre(rol);
        u.setRol(r);
        return u;
    }

    private static Curso curso(int id, boolean activo) {
        Curso c = new Curso();
        c.setId(id);
        c.setNombre("Base de Datos");
        c.setActivo(activo);
        return c;
    }

    @BeforeEach
    void preparar() {
        consultaRepository = Mockito.mock(ConsultaRepository.class);
        respuestaRepository = Mockito.mock(RespuestaRepository.class);
        cursoRepository = Mockito.mock(CursoRepository.class);
        usuarioRepository = Mockito.mock(UsuarioRepository.class);
        servicio = new ForoService(consultaRepository, respuestaRepository, cursoRepository, usuarioRepository,
                new LimitadorConsultas(100, Clock.systemUTC()), Clock.systemDefaultZone());

        Usuario autor = usuario(AUTOR, "Ana María", "Quispe Rojas", "ESTUDIANTE");
        when(usuarioRepository.findById(AUTOR)).thenReturn(Optional.of(autor));
        when(usuarioRepository.findById(OTRO)).thenReturn(Optional.of(usuario(OTRO, "Lucía", "Ramírez", "MENTOR")));
        when(cursoRepository.findById(4)).thenReturn(Optional.of(curso(4, true)));
        when(cursoRepository.findById(99)).thenReturn(Optional.of(curso(99, false)));

        consulta = new Consulta();
        consulta.setId(10);
        consulta.setAutor(autor);
        consulta.setCurso(curso(4, true));
        consulta.setTitulo("¿Cómo funciona un JOIN?");
        consulta.setContenido("No entiendo la diferencia entre INNER y LEFT JOIN.");
        consulta.setFechaCreacion(LocalDateTime.now().minusHours(2));
        when(consultaRepository.findByIdAndOcultoFalse(10)).thenReturn(Optional.of(consulta));
        when(consultaRepository.save(any(Consulta.class))).thenAnswer(i -> {
            Consulta c = i.getArgument(0);
            c.setId(11);
            return c;
        });

        when(respuestaRepository.findByConsultaId(10)).thenReturn(respuestas);
        when(respuestaRepository.findByConsultaIdAndOcultoFalseOrderByUtilDescFechaCreacionAscIdAsc(anyInt()))
                .thenAnswer(i -> respuestas.stream().filter(r -> !r.isOculto()).toList());
    }

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }

    private Respuesta respuesta(int id, int idAutor, String texto) {
        Respuesta r = new Respuesta();
        r.setId(id);
        r.setConsulta(consulta);
        r.setAutor(idAutor == AUTOR ? consulta.getAutor() : usuarioRepository.findById(idAutor).orElseThrow());
        r.setContenido(texto);
        r.setFechaCreacion(LocalDateTime.now());
        respuestas.add(r);
        return r;
    }

    private static DatosInvalidosException invalido(Runnable accion) {
        return assertThrows(DatosInvalidosException.class, accion::run);
    }

    // ---------- crear ----------

    @Test
    void creaUnaConsultaAsociadaAUnCurso() {
        ConsultaDetalle d = servicio.crear(AUTOR, new NuevaConsulta(4, "  ¿Cómo   funciona un JOIN?  ", "No entiendo la diferencia entre INNER y LEFT."));

        ArgumentCaptor<Consulta> guardada = ArgumentCaptor.forClass(Consulta.class);
        verify(consultaRepository).save(guardada.capture());
        assertEquals("¿Cómo funciona un JOIN?", guardada.getValue().getTitulo());
        assertEquals(4, guardada.getValue().getCurso().getId());
        assertEquals(AUTOR, guardada.getValue().getAutor().getId());
        assertNotNull(guardada.getValue().getFechaCreacion());
        assertTrue(d.esMia());
        assertTrue(d.respuestas().isEmpty());
    }

    @Test
    void validaElTituloElContenidoYElCurso() {
        DatosInvalidosException e = invalido(() -> servicio.crear(AUTOR, new NuevaConsulta(null, "Hola", "corto")));

        assertEquals(3, e.getCampos().size());
        assertTrue(e.getCampos().containsKey("titulo"));
        assertTrue(e.getCampos().containsKey("contenido"));
        assertTrue(e.getCampos().containsKey("cursoId"));
        verify(consultaRepository, never()).save(any());
    }

    @Test
    void unCursoInactivoOInexistenteNoSirve() {
        assertTrue(invalido(() -> servicio.crear(AUTOR, new NuevaConsulta(99, "Un título válido", "Un contenido suficientemente largo")))
                .getCampos().containsKey("cursoId"));
        assertTrue(invalido(() -> servicio.crear(AUTOR, new NuevaConsulta(777, "Un título válido", "Un contenido suficientemente largo")))
                .getCampos().containsKey("cursoId"));
    }

    @Test
    void ellimiteDeLargoSeRespeta() {
        assertDoesNotThrow(() -> servicio.crear(AUTOR, new NuevaConsulta(4, "a".repeat(150), "b".repeat(2000))));
        assertTrue(invalido(() -> servicio.crear(AUTOR, new NuevaConsulta(4, "a".repeat(151), "b".repeat(20)))).getCampos().containsKey("titulo"));
        assertTrue(invalido(() -> servicio.crear(AUTOR, new NuevaConsulta(4, "a".repeat(20), "b".repeat(2001)))).getCampos().containsKey("contenido"));
    }

    @Test
    void publicarDemasiadoRapidoDa429() {
        ForoService limitado = new ForoService(consultaRepository, respuestaRepository, cursoRepository, usuarioRepository,
                new LimitadorConsultas(1, Clock.systemUTC()), Clock.systemDefaultZone());
        limitado.crear(AUTOR, new NuevaConsulta(4, "Primera consulta", "Contenido de la primera consulta"));

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> limitado.crear(AUTOR, new NuevaConsulta(4, "Segunda consulta", "Contenido de la segunda consulta")));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getEstado());
    }

    // ---------- responder ----------

    @Test
    void otroUsuarioRespondeYSuRespuestaApareceEnLaConsulta() {
        ArgumentCaptor<Respuesta> guardada = ArgumentCaptor.forClass(Respuesta.class);
        when(respuestaRepository.save(guardada.capture())).thenAnswer(i -> {
            Respuesta r = i.getArgument(0);
            r.setId(50);
            respuestas.add(r);
            return r;
        });

        ConsultaDetalle d = servicio.responder(OTRO, 10, new NuevaRespuesta("Un INNER JOIN trae solo las coincidencias."));

        assertEquals(OTRO, guardada.getValue().getAutor().getId());
        assertEquals(1, d.respuestas().size());
        assertEquals("Lucía Ramírez", d.respuestas().get(0).autor().nombre());
        assertEquals("MENTOR", d.respuestas().get(0).autor().rol());
        assertTrue(d.respuestas().get(0).esMia());   // para quien la escribió, la respuesta es suya
    }

    @Test
    void laRespuestaDebeTenerContenido() {
        assertTrue(invalido(() -> servicio.responder(OTRO, 10, new NuevaRespuesta("corta"))).getCampos().containsKey("contenido"));
        assertTrue(invalido(() -> servicio.responder(OTRO, 10, new NuevaRespuesta(null))).getCampos().containsKey("contenido"));
    }

    @Test
    void noSePuedeResponderUnaConsultaOcultaOInexistente() {
        when(consultaRepository.findByIdAndOcultoFalse(77)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ReglaNegocioException.class, () -> servicio.responder(OTRO, 77, new NuevaRespuesta("Una respuesta larga"))).getEstado());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ReglaNegocioException.class, () -> servicio.detalle(OTRO, 77)).getEstado());
    }

    // ---------- útil ----------

    @Test
    void soloElAutorDeLaConsultaMarcaUnaRespuestaComoUtil() {
        respuesta(20, OTRO, "Respuesta de otro");

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.marcarUtil(OTRO, 10, 20));

        assertEquals(HttpStatus.FORBIDDEN, e.getEstado());
        assertFalse(respuestas.get(0).isUtil());
    }

    @Test
    void marcaUnaRespuestaComoUtilYDesmarcaLasDemas() {
        Respuesta a = respuesta(20, OTRO, "Primera respuesta");
        Respuesta b = respuesta(21, OTRO, "Segunda respuesta");
        a.setUtil(true);

        ConsultaDetalle d = servicio.marcarUtil(AUTOR, 10, 21);

        assertFalse(a.isUtil());
        assertTrue(b.isUtil());
        assertTrue(d.esMia());
        verify(respuestaRepository).saveAll(respuestas);
    }

    @Test
    void marcarDeNuevoLaMismaRespuestaLaDesmarca() {
        Respuesta a = respuesta(20, OTRO, "Primera respuesta");
        a.setUtil(true);

        servicio.marcarUtil(AUTOR, 10, 20);

        assertFalse(a.isUtil());
    }

    @Test
    void unaRespuestaOcultaOAjenaNoSePuedeMarcar() {
        Respuesta oculta = respuesta(20, OTRO, "Respuesta oculta");
        ReflectionTestUtils.setField(oculta, "oculto", true);

        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ReglaNegocioException.class, () -> servicio.marcarUtil(AUTOR, 10, 20)).getEstado());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ReglaNegocioException.class, () -> servicio.marcarUtil(AUTOR, 10, 999)).getEstado());
    }

    @Test
    void laDetalleNoIncluyeLasRespuestasOcultas() {
        respuesta(20, OTRO, "Visible");
        Respuesta oculta = respuesta(21, OTRO, "Oculta");
        ReflectionTestUtils.setField(oculta, "oculto", true);

        assertEquals(1, servicio.detalle(AUTOR, 10).respuestas().size());
    }

    // ---------- listar ----------

    @Test
    void listaLasConsultasConSusRespuestasYSiEstanResueltas() {
        when(consultaRepository.buscar(any(), any(), any(Pageable.class))).thenReturn(List.of(consulta));
        when(respuestaRepository.resumenPorConsultas(anyCollection())).thenReturn(List.<Object[]>of(new Object[]{10, 3L, 1L}));

        List<ConsultaResumen> r = servicio.listar(null, null);

        assertEquals(1, r.size());
        assertEquals(3, r.get(0).respuestas());
        assertTrue(r.get(0).resuelta());
        assertEquals("Ana Quispe", r.get(0).autor().nombre());
        assertEquals("Base de Datos", r.get(0).curso());
    }

    @Test
    void sinRespuestasLaConsultaNoEstaResuelta() {
        when(consultaRepository.buscar(any(), any(), any(Pageable.class))).thenReturn(List.of(consulta));
        when(respuestaRepository.resumenPorConsultas(anyCollection())).thenReturn(List.<Object[]>of());

        ConsultaResumen r = servicio.listar(null, null).get(0);

        assertEquals(0, r.respuestas());
        assertFalse(r.resuelta());
    }

    @Test
    void laBusquedaSeEnviaEnMinusculasYElFiltroDeCursoSePasa() {
        when(consultaRepository.buscar(any(), any(), any(Pageable.class))).thenReturn(List.of());

        assertTrue(servicio.listar(4, "  JOIN ").isEmpty());

        verify(consultaRepository).buscar(eq(4), eq("%join%"), any(Pageable.class));
    }

    @Test
    void sinBusquedaNoFiltraPorTexto() {
        when(consultaRepository.buscar(any(), any(), any(Pageable.class))).thenReturn(List.of());

        servicio.listar(null, "   ");

        verify(consultaRepository).buscar(eq(null), eq(null), any(Pageable.class));
    }

    @Test
    void elExtractoSeRecortaSiLaConsultaEsLarga() {
        consulta.setContenido("palabra ".repeat(100));
        when(consultaRepository.buscar(any(), any(), any(Pageable.class))).thenReturn(List.of(consulta));
        when(respuestaRepository.resumenPorConsultas(anyCollection())).thenReturn(List.<Object[]>of());

        String extracto = servicio.listar(null, null).get(0).extracto();

        assertTrue(extracto.length() <= ForoService.MAX_EXTRACTO);
        assertTrue(extracto.endsWith("…"));
    }

    @Test
    void elNombreDelAutorSoloMuestraPrimerNombreYPrimerApellido() {
        assertEquals("Ana Quispe", ForoService.autor(usuario(1, "Ana María", "Quispe Rojas", "ESTUDIANTE")).nombre());
        assertEquals("Ana", ForoService.autor(usuario(1, "Ana", "-", "ESTUDIANTE")).nombre());
    }
}
