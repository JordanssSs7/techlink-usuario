package com.techlink.service;

import com.techlink.dto.SesionHistorialResponse;
import com.techlink.exception.SesionException;
import com.techlink.model.Calificacion;
import com.techlink.model.Curso;
import com.techlink.model.Sesion;
import com.techlink.model.SesionNota;
import com.techlink.repository.CalificacionRepository;
import com.techlink.repository.RecursoRepository;
import com.techlink.repository.SesionNotaRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

import static com.techlink.service.PruebaSesiones.USUARIO_ESTUDIANTE;
import static com.techlink.service.PruebaSesiones.USUARIO_MENTOR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

// Pruebas de HU-20: historial de sesiones con filtro por estado y las acciones que corresponden a cada una.
class SesionHistorialServiceTest {

    private SesionRepository sesionRepository;
    private CalificacionRepository calificacionRepository;
    private SesionNotaRepository notaRepository;
    private RecursoRepository recursoRepository;
    private SesionHistorialService servicio;

    private Sesion programada;   // dentro de 5 horas
    private Sesion proxima;      // dentro de 1 hora (ya no se puede modificar)
    private Sesion completada;   // terminó hace 3 horas
    private Sesion cancelada;

    @BeforeEach
    void preparar() {
        sesionRepository = Mockito.mock(SesionRepository.class);
        calificacionRepository = Mockito.mock(CalificacionRepository.class);
        notaRepository = Mockito.mock(SesionNotaRepository.class);
        recursoRepository = Mockito.mock(RecursoRepository.class);
        servicio = new SesionHistorialService(sesionRepository, calificacionRepository, notaRepository, recursoRepository);

        LocalDateTime ahora = LocalDateTime.now();
        programada = PruebaSesiones.sesion(1, ahora.plusHours(5), 60, Sesion.EstadoSesion.CONFIRMADA);
        proxima = PruebaSesiones.sesion(2, ahora.plusHours(1), 60, Sesion.EstadoSesion.CONFIRMADA);
        completada = PruebaSesiones.sesion(3, ahora.minusHours(4), 60, Sesion.EstadoSesion.CONFIRMADA);
        cancelada = PruebaSesiones.sesion(4, ahora.plusHours(8), 60, Sesion.EstadoSesion.CANCELADA);

        Curso bd = new Curso();
        bd.setNombre("Base de Datos");
        completada.setCurso(bd);

        List<Sesion> todas = List.of(programada, proxima, completada, cancelada);
        when(sesionRepository.historialDeEstudiante(USUARIO_ESTUDIANTE)).thenReturn(todas);
        when(sesionRepository.historialDeMentor(USUARIO_MENTOR)).thenReturn(todas);
        when(calificacionRepository.buscarPorSesiones(any())).thenReturn(List.of());
        when(notaRepository.buscarPorSesiones(any())).thenReturn(List.of());
        when(recursoRepository.contarPorSesiones(any(), anyBoolean())).thenReturn(List.<Object[]>of());
    }

    private SesionHistorialResponse buscar(List<SesionHistorialResponse> lista, int id) {
        return lista.stream().filter(s -> s.id() == id).findFirst().orElseThrow();
    }

    @Test
    void sinFiltroDevuelveTodasConSuEstado() {
        List<SesionHistorialResponse> lista = servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", null);

        assertEquals(4, lista.size());
        assertEquals("PROGRAMADA", buscar(lista, 1).estado());
        assertEquals("COMPLETADA", buscar(lista, 3).estado());
        assertEquals("CANCELADA", buscar(lista, 4).estado());
    }

    @Test
    void filtraPorEstadoSinImportarMayusculas() {
        assertEquals(2, servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", "programada").size());
        assertEquals(1, servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", "COMPLETADA").size());
        assertEquals(1, servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", "CANCELADA").size());
        assertEquals(4, servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", "  ").size());
    }

    @Test
    void unEstadoDesconocidoEsInvalido() {
        SesionException e = assertThrows(SesionException.class,
                () -> servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", "BORRADA"));

        assertEquals(HttpStatus.BAD_REQUEST, e.getEstado());
    }

    @Test
    void sinSesionesDevuelveListaVacia() {
        when(sesionRepository.historialDeEstudiante(USUARIO_ESTUDIANTE)).thenReturn(List.of());

        assertTrue(servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", null).isEmpty());
    }

    @Test
    void elEstudianteVeAlMentorYElCurso() {
        SesionHistorialResponse r = buscar(servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", null), 3);

        assertEquals("Lucía Ramírez", r.contraparte());
        assertEquals("Base de Datos", r.curso());
        assertNull(buscar(servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", null), 1).curso());
    }

    @Test
    void elMentorVeAlEstudiante() {
        SesionHistorialResponse r = buscar(servicio.listar(USUARIO_MENTOR, "MENTOR", null), 3);

        assertEquals("Ana Quispe", r.contraparte());
    }

    @Test
    void puedeModificarSoloConDosHorasDeAnticipacion() {
        List<SesionHistorialResponse> lista = servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", null);

        assertTrue(buscar(lista, 1).puedeModificar());
        assertFalse(buscar(lista, 2).puedeModificar());   // empieza en 1 hora
        assertFalse(buscar(lista, 3).puedeModificar());   // ya terminó
        assertFalse(buscar(lista, 4).puedeModificar());   // cancelada
    }

    @Test
    void elMentorNuncaPuedeCancelarOReprogramar() {
        servicio.listar(USUARIO_MENTOR, "MENTOR", null)
                .forEach(s -> assertFalse(s.puedeModificar()));
    }

    @Test
    void puedeCalificarSoloUnaSesionCompletadaYSinCalificar() {
        List<SesionHistorialResponse> lista = servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", null);

        assertTrue(buscar(lista, 3).puedeCalificar());
        assertFalse(buscar(lista, 1).puedeCalificar());
        assertFalse(buscar(lista, 4).puedeCalificar());
    }

    @Test
    void unaSesionYaCalificadaMuestraSusEstrellasYNoSeCalificaOtraVez() {
        Calificacion c = new Calificacion();
        c.setSesion(completada);
        c.setEstrellas(4);
        c.setComentario("Muy buena");
        when(calificacionRepository.buscarPorSesiones(any())).thenReturn(List.of(c));

        SesionHistorialResponse r = buscar(servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", null), 3);

        assertEquals(4, r.estrellas());
        assertEquals("Muy buena", r.comentario());
        assertFalse(r.puedeCalificar());
    }

    @Test
    void elMentorNoCalifica() {
        assertFalse(buscar(servicio.listar(USUARIO_MENTOR, "MENTOR", null), 3).puedeCalificar());
    }

    @Test
    void elMentorEditaNotasDesdeQueEmpiezaLaSesionYSoloSiNoEstaCancelada() {
        List<SesionHistorialResponse> lista = servicio.listar(USUARIO_MENTOR, "MENTOR", null);

        assertTrue(buscar(lista, 3).puedeEditarNota());    // completada
        assertFalse(buscar(lista, 1).puedeEditarNota());   // aún no empieza
        assertFalse(buscar(lista, 4).puedeEditarNota());   // cancelada
        servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", null)
                .forEach(s -> assertFalse(s.puedeEditarNota()));
    }

    @Test
    void laNotaLlegaAlHistorialDeLosDos() {
        SesionNota nota = new SesionNota();
        nota.setSesion(completada);
        nota.setContenido("Tarea: ejercicios 3 y 4");
        when(notaRepository.buscarPorSesiones(any())).thenReturn(List.of(nota));

        assertEquals("Tarea: ejercicios 3 y 4",
                buscar(servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", null), 3).nota());
        assertEquals("Tarea: ejercicios 3 y 4",
                buscar(servicio.listar(USUARIO_MENTOR, "MENTOR", null), 3).nota());
    }

    @Test
    void unaSesionCanceladaNoMuestraElEnlaceDeLaReunion() {
        List<SesionHistorialResponse> lista = servicio.listar(USUARIO_ESTUDIANTE, "ESTUDIANTE", null);

        assertNull(buscar(lista, 4).enlaceReunion());
        assertNotNull(buscar(lista, 1).enlaceReunion());
    }
}
