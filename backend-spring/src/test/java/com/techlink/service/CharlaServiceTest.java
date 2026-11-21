package com.techlink.service;

import com.techlink.dto.CharlaResponse;
import com.techlink.dto.CharlasRecomendadasResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Charla;
import com.techlink.model.CharlaInscripcion;
import com.techlink.model.Curso;
import com.techlink.model.Estudiante;
import com.techlink.repository.CharlaInscripcionRepository;
import com.techlink.repository.CharlaRepository;
import com.techlink.repository.EstudianteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.techlink.service.PruebaSesiones.USUARIO_ESTUDIANTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-06 (recomendar charlas) y HU-25 (inscribirse y ver "Mis charlas").
class CharlaServiceTest {

    // "Hoy" para las pruebas: 20/11/2026 a las 12:00.
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 12, 0);
    private static final LocalDate HOY = AHORA.toLocalDate();

    private CharlaRepository charlaRepository;
    private CharlaInscripcionRepository inscripcionRepository;
    private EstudianteRepository estudianteRepository;
    private CharlaService servicio;
    private Estudiante estudiante;

    @BeforeEach
    void preparar() {
        charlaRepository = Mockito.mock(CharlaRepository.class);
        inscripcionRepository = Mockito.mock(CharlaInscripcionRepository.class);
        estudianteRepository = Mockito.mock(EstudianteRepository.class);
        ZoneId zona = ZoneId.systemDefault();
        servicio = new CharlaService(charlaRepository, inscripcionRepository, estudianteRepository,
                Clock.fixed(AHORA.atZone(zona).toInstant(), zona));

        estudiante = new Estudiante();
        estudiante.setId(1);
        estudiante.setUsuario(PruebaSesiones.usuario(USUARIO_ESTUDIANTE, "Ana", "Quispe", "ana@tecsup.edu.pe"));
        when(estudianteRepository.findByUsuarioId(USUARIO_ESTUDIANTE)).thenReturn(Optional.of(estudiante));
        when(inscripcionRepository.idsDeCharlas(USUARIO_ESTUDIANTE)).thenReturn(List.of());
    }

    private static Curso curso(int id, String nombre) {
        Curso c = new Curso();
        c.setId(id);
        c.setNombre(nombre);
        return c;
    }

    private static Charla charla(int id, String nombre, String tematica, LocalDate fecha, int horaInicio, int horaFin) {
        Charla c = new Charla();
        c.setId(id);
        c.setNombre(nombre);
        c.setTematica(tematica);
        c.setFecha(fecha);
        c.setHoraInicio(LocalTime.of(horaInicio, 0));
        c.setHoraFin(LocalTime.of(horaFin, 0));
        c.setEnlace("https://meet.test/" + id);
        c.setActivo(true);
        return c;
    }

    private static ReglaNegocioException error(Runnable accion) {
        return assertThrows(ReglaNegocioException.class, accion::run);
    }

    // ---------------------------------------------------------------- HU-06

    @Test
    void recomiendaLasCharlasQueCoincidenConUnInteresAunqueEsteEnPlural() {
        estudiante.getUsuario().getIntereses().add("Base de datos");
        Charla sql = charla(1, "SQL para entrevistas", "Bases de Datos", HOY.plusDays(2), 18, 19);
        Charla web = charla(2, "Diseño con React", "Desarrollo web", HOY.plusDays(3), 18, 19);
        when(charlaRepository.vigentes(any(), any())).thenReturn(List.of(sql, web));

        CharlasRecomendadasResponse r = servicio.recomendadas(USUARIO_ESTUDIANTE);

        assertEquals(List.of(1), r.recomendadas().stream().map(CharlaResponse::id).toList());
        assertEquals(List.of(2), r.otras().stream().map(CharlaResponse::id).toList());
        assertTrue(r.perfilCompleto());
    }

    @Test
    void elInteresSeBuscaSinDistinguirMayusculasNiTildes() {
        estudiante.getUsuario().getIntereses().add("PROGRAMACION");
        Charla c = charla(1, "Trucos de programación", "Carrera", HOY.plusDays(1), 18, 19);
        when(charlaRepository.vigentes(any(), any())).thenReturn(List.of(c));

        CharlasRecomendadasResponse r = servicio.recomendadas(USUARIO_ESTUDIANTE);

        assertEquals(1, r.recomendadas().size());
        assertEquals("Coincide con tu interés en PROGRAMACION", r.recomendadas().get(0).razon());
    }

    @Test
    void recomiendaLaCharlaQueRefuerzaUnCursoCritico() {
        estudiante.getCursosCriticos().add(curso(7, "Base de Datos"));
        Charla c = charla(1, "Índices en MySQL", "Datos", HOY.plusDays(1), 18, 19);
        c.setCurso(curso(7, "Base de Datos"));
        Charla otra = charla(2, "Liderazgo", "Soft skills", HOY.plusDays(1), 20, 21);
        otra.setCurso(curso(9, "Física"));
        when(charlaRepository.vigentes(any(), any())).thenReturn(List.of(c, otra));

        CharlasRecomendadasResponse r = servicio.recomendadas(USUARIO_ESTUDIANTE);

        assertEquals(List.of(1), r.recomendadas().stream().map(CharlaResponse::id).toList());
        assertEquals("Refuerza tu curso Base de Datos", r.recomendadas().get(0).razon());
        assertEquals(List.of(2), r.otras().stream().map(CharlaResponse::id).toList());
        assertNull(r.otras().get(0).razon());
    }

    @Test
    void sinInteresesNiCursosElPerfilNoEstaCompletoYTodasVanEnOtras() {
        Charla c = charla(1, "Charla", "Tema", HOY.plusDays(1), 18, 19);
        when(charlaRepository.vigentes(any(), any())).thenReturn(List.of(c));

        CharlasRecomendadasResponse r = servicio.recomendadas(USUARIO_ESTUDIANTE);

        assertFalse(r.perfilCompleto());
        assertTrue(r.recomendadas().isEmpty());
        assertEquals(1, r.otras().size());
    }

    @Test
    void marcaComoInscritasLasCharlasDondeYaEstaAnotado() {
        Charla c = charla(1, "Charla", "Tema", HOY.plusDays(1), 18, 19);
        when(charlaRepository.vigentes(any(), any())).thenReturn(List.of(c));
        when(inscripcionRepository.idsDeCharlas(USUARIO_ESTUDIANTE)).thenReturn(List.of(1));

        assertTrue(servicio.recomendadas(USUARIO_ESTUDIANTE).otras().get(0).inscrito());
    }

    @Test
    void soloLosEstudiantesPuedenVerCharlas() {
        when(estudianteRepository.findByUsuarioId(99)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.FORBIDDEN, error(() -> servicio.recomendadas(99)).getEstado());
    }

    // ---------------------------------------------------------------- HU-25

    @Test
    void seInscribeAUnaCharlaVigente() {
        Charla c = charla(1, "Charla", "Tema", HOY.plusDays(1), 18, 19);
        when(charlaRepository.findById(1)).thenReturn(Optional.of(c));
        when(inscripcionRepository.existsByIdCharlaAndIdUsuario(1, USUARIO_ESTUDIANTE)).thenReturn(false);

        CharlaResponse r = servicio.inscribir(USUARIO_ESTUDIANTE, 1);

        verify(inscripcionRepository).save(any(CharlaInscripcion.class));
        assertTrue(r.inscrito());
        assertTrue(r.vigente());
        assertEquals("https://meet.test/1", r.enlace());
    }

    @Test
    void noPuedeInscribirseDosVecesALaMismaCharla() {
        Charla c = charla(1, "Charla", "Tema", HOY.plusDays(1), 18, 19);
        when(charlaRepository.findById(1)).thenReturn(Optional.of(c));
        when(inscripcionRepository.existsByIdCharlaAndIdUsuario(1, USUARIO_ESTUDIANTE)).thenReturn(true);

        ReglaNegocioException e = error(() -> servicio.inscribir(USUARIO_ESTUDIANTE, 1));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
        assertEquals("Ya estás inscrito en esta charla.", e.getMessage());
        verify(inscripcionRepository, never()).save(any());
    }

    @Test
    void noPuedeInscribirseAUnaCharlaQueYaTermino() {
        Charla ayer = charla(1, "Charla", "Tema", HOY.minusDays(1), 18, 19);
        Charla estaMañanaYaTermino = charla(2, "Charla", "Tema", HOY, 9, 11);
        when(charlaRepository.findById(1)).thenReturn(Optional.of(ayer));
        when(charlaRepository.findById(2)).thenReturn(Optional.of(estaMañanaYaTermino));

        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.inscribir(USUARIO_ESTUDIANTE, 1)).getEstado());
        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.inscribir(USUARIO_ESTUDIANTE, 2)).getEstado());
    }

    @Test
    void unaCharlaQueEmpezoPeroNoTerminoSigueVigente() {
        Charla enCurso = charla(1, "Charla", "Tema", HOY, 11, 13);
        when(charlaRepository.findById(1)).thenReturn(Optional.of(enCurso));

        assertTrue(servicio.inscribir(USUARIO_ESTUDIANTE, 1).vigente());
    }

    @Test
    void noPuedeInscribirseAUnaCharlaDesactivadaOInexistente() {
        Charla c = charla(1, "Charla", "Tema", HOY.plusDays(1), 18, 19);
        c.setActivo(false);
        when(charlaRepository.findById(1)).thenReturn(Optional.of(c));
        when(charlaRepository.findById(5)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.inscribir(USUARIO_ESTUDIANTE, 1)).getEstado());
        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.inscribir(USUARIO_ESTUDIANTE, 5)).getEstado());
    }

    @Test
    void misCharlasMuestraPrimeroLasProximasDeLaMasCercanaALaMasLejanaYLuegoLasPasadas() {
        Charla pasada = charla(1, "Pasada", "T", HOY.minusDays(5), 18, 19);
        Charla lejana = charla(2, "Lejana", "T", HOY.plusDays(10), 18, 19);
        Charla cercana = charla(3, "Cercana", "T", HOY.plusDays(1), 18, 19);
        // El repositorio las entrega de la más reciente a la más antigua.
        when(charlaRepository.inscritasDe(USUARIO_ESTUDIANTE)).thenReturn(List.of(lejana, cercana, pasada));

        List<CharlaResponse> r = servicio.mias(USUARIO_ESTUDIANTE);

        assertEquals(List.of(3, 2, 1), r.stream().map(CharlaResponse::id).toList());
        assertTrue(r.stream().allMatch(CharlaResponse::inscrito));
        assertFalse(r.get(2).vigente());
    }

    @Test
    void anulaLaInscripcionDeUnaCharlaVigente() {
        Charla c = charla(1, "Charla", "Tema", HOY.plusDays(1), 18, 19);
        CharlaInscripcion inscripcion = new CharlaInscripcion(1, USUARIO_ESTUDIANTE);
        when(inscripcionRepository.findByIdCharlaAndIdUsuario(1, USUARIO_ESTUDIANTE)).thenReturn(Optional.of(inscripcion));
        when(charlaRepository.findById(1)).thenReturn(Optional.of(c));

        servicio.cancelar(USUARIO_ESTUDIANTE, 1);

        verify(inscripcionRepository).delete(inscripcion);
    }

    @Test
    void noPuedeAnularUnaInscripcionQueNoExisteNiUnaCharlaTerminada() {
        when(inscripcionRepository.findByIdCharlaAndIdUsuario(1, USUARIO_ESTUDIANTE)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.cancelar(USUARIO_ESTUDIANTE, 1)).getEstado());

        Charla pasada = charla(2, "Charla", "Tema", HOY.minusDays(1), 18, 19);
        when(inscripcionRepository.findByIdCharlaAndIdUsuario(2, USUARIO_ESTUDIANTE))
                .thenReturn(Optional.of(new CharlaInscripcion(2, USUARIO_ESTUDIANTE)));
        when(charlaRepository.findById(2)).thenReturn(Optional.of(pasada));
        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.cancelar(USUARIO_ESTUDIANTE, 2)).getEstado());
    }

    @Test
    void normalizarQuitaTildesYMayusculas() {
        assertEquals("programacion avanzada", CharlaService.normalizar("  Programación AVANZADA "));
        assertEquals("", CharlaService.normalizar(null));
    }
}
