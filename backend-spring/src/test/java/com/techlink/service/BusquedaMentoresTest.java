package com.techlink.service;

import com.techlink.dto.BusquedaMentoresResponse;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.model.Curso;
import com.techlink.model.EstadoUsuario;
import com.techlink.model.Mentor;
import com.techlink.model.MentorDisponibilidad;
import com.techlink.repository.CalificacionRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.UsuarioFotoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

// Pruebas de HU-16: buscar mentores por curso, horario y calificación mínima.
class BusquedaMentoresTest {

    private static final int BASES_DE_DATOS = 1;
    private static final int PROGRAMACION = 2;

    private MentorRepository mentorRepository;
    private MentorPublicoService servicio;
    private final List<Mentor> mentores = new ArrayList<>();

    @BeforeEach
    void preparar() {
        mentorRepository = Mockito.mock(MentorRepository.class);
        InsigniaService insigniaService = Mockito.mock(InsigniaService.class);
        servicio = new MentorPublicoService(mentorRepository, Mockito.mock(CalificacionRepository.class),
                Mockito.mock(UsuarioFotoRepository.class), insigniaService);
        when(insigniaService.actividad(anyInt())).thenReturn(new InsigniaService.Actividad(0, 0));
        when(mentorRepository.findAll()).thenReturn(mentores);

        // Ana: Bases de datos, 4.8, lunes de 08:00 a 12:00.
        mentores.add(mentor(1, "Ana", 4.8, BASES_DE_DATOS, horario(1, 8, 12)));
        // Beto: Bases de datos y Programación, 3.9, lunes por la tarde y martes por la mañana.
        mentores.add(mentor(2, "Beto", 3.9, BASES_DE_DATOS, horario(1, 14, 18), horario(2, 8, 12)));
        // Carla: Programación, 4.5, miércoles por la noche.
        mentores.add(mentor(3, "Carla", 4.5, PROGRAMACION, horario(3, 18, 21)));
        // Diego: Programación, sin calificaciones, lunes por la mañana.
        mentores.add(mentor(4, "Diego", null, PROGRAMACION, horario(1, 9, 11)));
        // Elisa: Bases de datos, 5.0, pero su cuenta está inactiva (no aparece en ningún resultado).
        Mentor elisa = mentor(5, "Elisa", 5.0, BASES_DE_DATOS, horario(1, 8, 12));
        elisa.getUsuario().setEstado(EstadoUsuario.INACTIVO);
        mentores.add(elisa);
    }

    private static MentorDisponibilidad horario(int dia, int desde, int hasta) {
        MentorDisponibilidad d = new MentorDisponibilidad();
        d.setDiaSemana(dia);
        d.setHoraInicio(LocalTime.of(desde, 0));
        d.setHoraFin(LocalTime.of(hasta, 0));
        d.setActivo(true);
        return d;
    }

    private static Mentor mentor(int id, String nombre, Double promedio, int curso, MentorDisponibilidad... horarios) {
        Mentor m = new Mentor();
        m.setId(id);
        m.setUsuario(PruebaSesiones.usuario(100 + id, nombre, "Apellido", nombre.toLowerCase() + "@tecsup.edu.pe"));
        m.getUsuario().setEstado(EstadoUsuario.ACTIVO);
        m.setReputacion(promedio == null ? BigDecimal.ZERO : BigDecimal.valueOf(promedio));
        m.setTotalCalificaciones(promedio == null ? 0 : 5);
        Curso c = new Curso();
        c.setId(curso);
        c.setNombre("Curso " + curso);
        m.setCursos(new LinkedHashSet<>(List.of(c)));
        if (id == 2) {
            Curso otro = new Curso();
            otro.setId(PROGRAMACION);
            otro.setNombre("Curso 2");
            m.getCursos().add(otro);
        }
        m.setDisponibilidad(new ArrayList<>(List.of(horarios)));
        return m;
    }

    private List<String> nombres(BusquedaMentoresResponse r) {
        return r.mentores().stream().map(x -> x.nombres()).toList();
    }

    @Test
    void sinFiltrosDevuelveElRankingDeLosMentoresActivos() {
        BusquedaMentoresResponse r = servicio.buscar(null, null, null, null, null);

        assertEquals(List.of("Ana", "Carla", "Beto", "Diego"), nombres(r));
        assertEquals(4, r.total());
        assertNull(r.mensaje());
        assertTrue(r.sugerencias().isEmpty());
    }

    @Test
    void filtraPorCurso() {
        assertEquals(List.of("Ana", "Beto"), nombres(servicio.buscar(BASES_DE_DATOS, null, null, null, null)));
        assertEquals(List.of("Carla", "Beto", "Diego"), nombres(servicio.buscar(PROGRAMACION, null, null, null, null)));
    }

    @Test
    void filtraPorCalificacionMinima() {
        assertEquals(List.of("Ana", "Carla"), nombres(servicio.buscar(null, null, null, null, 4.5)));
        assertEquals(List.of("Ana", "Carla", "Beto"), nombres(servicio.buscar(null, null, null, null, 3.0)));
    }

    @Test
    void filtraPorDia() {
        assertEquals(List.of("Ana", "Beto", "Diego"), nombres(servicio.buscar(null, 1, null, null, null)));
        assertEquals(List.of("Carla"), nombres(servicio.buscar(null, 3, null, null, null)));
    }

    @Test
    void filtraPorFranjaHoraria() {
        // Lunes de 08:00 a 12:00 (mañana): Ana y Diego; Beto solo atiende los lunes por la tarde.
        assertEquals(List.of("Ana", "Diego"), nombres(servicio.buscar(null, 1, "08:00", "12:00", null)));
        // Basta con que el horario se cruce: 11:00 a 15:00 toca a Ana (hasta las 12) y a Beto (desde las 14); Diego termina a las 11.
        assertEquals(List.of("Ana", "Beto"), nombres(servicio.buscar(null, 1, "11:00", "15:00", null)));
        // Una franja que empieza justo cuando el mentor termina no cuenta.
        assertEquals(List.of("Beto"), nombres(servicio.buscar(null, 1, "12:00", "20:00", null)));
    }

    @Test
    void laFranjaSinDiaBuscaEnCualquierDia() {
        assertEquals(List.of("Carla"), nombres(servicio.buscar(null, null, "19:00", "22:00", null)));
    }

    @Test
    void losFiltrosSeCombinan() {
        assertEquals(List.of("Ana"), nombres(servicio.buscar(BASES_DE_DATOS, 1, "08:00", "10:00", 4.0)));
    }

    @Test
    void unHorarioDesactivadoNoCuenta() {
        mentores.get(2).getDisponibilidad().get(0).setActivo(false);

        assertEquals(List.of(), nombres(servicio.buscar(null, 3, null, null, null)));
    }

    @Test
    void sinResultadosExplicaQueFiltroAmpliar() {
        // Bases de datos + miércoles + 4.5: nadie. Sin el curso aparece Carla; sin el horario, Ana; sin la nota, nadie.
        BusquedaMentoresResponse r = servicio.buscar(BASES_DE_DATOS, 3, null, null, 4.5);

        assertEquals(0, r.total());
        assertEquals("No encontramos mentores con esos filtros.", r.mensaje());
        assertEquals(2, r.sugerencias().size());
        assertTrue(r.sugerencias().stream().anyMatch(s -> s.quitar().equals("curso") && s.resultados() == 1));
        assertTrue(r.sugerencias().stream().anyMatch(s -> s.quitar().equals("horario") && s.resultados() == 1));
        assertTrue(r.sugerencias().get(0).texto().contains("1 mentor."));
    }

    @Test
    void lasSugerenciasVanDelFiltroQueMasMentoresDevuelve() {
        // Programación + lunes + 4.8: Beto/Diego dan lunes en Programación pero ninguno llega a 4.8.
        BusquedaMentoresResponse r = servicio.buscar(PROGRAMACION, 1, null, null, 4.8);

        assertEquals(0, r.total());
        // Sin la nota aparecen Beto y Diego (2); sin el horario, nadie llega a 4.8 en Programación (no se sugiere);
        // sin el curso aparece Ana (1).
        assertEquals("calificacion", r.sugerencias().get(0).quitar());
        assertEquals(2, r.sugerencias().get(0).resultados());
        assertEquals("curso", r.sugerencias().get(1).quitar());
    }

    @Test
    void siNingunFiltroSoloNoAyudaSeSugiereQuitarlosTodos() {
        // Curso inexistente + calificación imposible de cumplir juntos con un día sin mentores.
        BusquedaMentoresResponse r = servicio.buscar(99, 7, null, null, 4.9);

        assertEquals(0, r.total());
        assertEquals(1, r.sugerencias().size());
        assertEquals("todos", r.sugerencias().get(0).quitar());
        assertEquals(4, r.sugerencias().get(0).resultados());
    }

    @Test
    void conUnSoloFiltroSinResultadosSeSugiereQuitarlo() {
        BusquedaMentoresResponse r = servicio.buscar(99, null, null, null, null);

        assertEquals(0, r.total());
        assertEquals("No encontramos mentores con esos filtros.", r.mensaje());
        assertEquals(1, r.sugerencias().size());
        assertEquals("curso", r.sugerencias().get(0).quitar());
        assertEquals(4, r.sugerencias().get(0).resultados());
    }

    @Test
    void sinMentoresRegistradosLoDiceAsi() {
        mentores.clear();

        BusquedaMentoresResponse r = servicio.buscar(BASES_DE_DATOS, null, null, null, null);

        assertEquals("Todavía no hay mentores registrados.", r.mensaje());
        assertTrue(r.sugerencias().isEmpty());
    }

    @Test
    void losFiltrosInvalidosSeRechazan() {
        assertTrue(assertThrows(DatosInvalidosException.class, () -> servicio.buscar(null, 8, null, null, null))
                .getCampos().containsKey("dia"));
        assertTrue(assertThrows(DatosInvalidosException.class, () -> servicio.buscar(null, null, null, null, 6.0))
                .getCampos().containsKey("calificacionMinima"));
        assertTrue(assertThrows(DatosInvalidosException.class, () -> servicio.buscar(null, null, "tarde", null, null))
                .getCampos().containsKey("desde"));
        assertTrue(assertThrows(DatosInvalidosException.class, () -> servicio.buscar(null, 1, "12:00", "10:00", null))
                .getCampos().containsKey("hasta"));
    }
}
