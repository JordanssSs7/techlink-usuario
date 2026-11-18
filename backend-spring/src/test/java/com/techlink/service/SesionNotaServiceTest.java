package com.techlink.service;

import com.techlink.dto.NotaResponse;
import com.techlink.exception.SesionException;
import com.techlink.model.Sesion;
import com.techlink.model.SesionNota;
import com.techlink.repository.SesionNotaRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.techlink.service.PruebaSesiones.USUARIO_ESTUDIANTE;
import static com.techlink.service.PruebaSesiones.USUARIO_MENTOR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-21: notas del mentor, editables hasta 24 horas después de la sesión y de solo lectura para el estudiante.
class SesionNotaServiceTest {

    private SesionRepository sesionRepository;
    private SesionNotaRepository notaRepository;
    private SesionNotaService servicio;

    @BeforeEach
    void preparar() {
        sesionRepository = Mockito.mock(SesionRepository.class);
        notaRepository = Mockito.mock(SesionNotaRepository.class);
        servicio = new SesionNotaService(sesionRepository, notaRepository);
        when(notaRepository.findBySesionId(1)).thenReturn(Optional.empty());
        when(notaRepository.saveAndFlush(any(SesionNota.class))).thenAnswer(i -> i.getArgument(0));
    }

    // Una sesión de 30 minutos que empezó hace "minutosAtras" minutos.
    private Sesion sesion(long minutosAtras, Sesion.EstadoSesion estado) {
        Sesion s = PruebaSesiones.sesion(1, LocalDateTime.now().minusMinutes(minutosAtras), 30, estado);
        when(sesionRepository.findById(1)).thenReturn(Optional.of(s));
        return s;
    }

    private static SesionException error(Runnable accion) {
        return assertThrows(SesionException.class, accion::run);
    }

    @Test
    void elMentorGuardaLasNotasDeUnaSesionTerminada() {
        sesion(120, Sesion.EstadoSesion.CONFIRMADA);

        NotaResponse r = servicio.guardar(USUARIO_MENTOR, 1, "  Repasar JOIN. Tarea: ejercicios 3 y 4.  ");

        ArgumentCaptor<SesionNota> nota = ArgumentCaptor.forClass(SesionNota.class);
        verify(notaRepository).saveAndFlush(nota.capture());
        assertEquals("Repasar JOIN. Tarea: ejercicios 3 y 4.", nota.getValue().getContenido());
        assertTrue(r.editable());
    }

    @Test
    void puedeEscribirNotasMientrasLaSesionEstaEnCurso() {
        sesion(10, Sesion.EstadoSesion.CONFIRMADA);

        assertDoesNotThrow(() -> servicio.guardar(USUARIO_MENTOR, 1, "Vamos bien"));
    }

    @Test
    void editaLaNotaExistenteSinCrearOtra() {
        sesion(120, Sesion.EstadoSesion.CONFIRMADA);
        SesionNota existente = new SesionNota();
        existente.setContenido("Nota vieja");
        when(notaRepository.findBySesionId(1)).thenReturn(Optional.of(existente));

        servicio.guardar(USUARIO_MENTOR, 1, "Nota nueva");

        assertEquals("Nota nueva", existente.getContenido());
        verify(notaRepository).saveAndFlush(existente);
    }

    @Test
    void noSePuedeEscribirAntesDeQueEmpiece() {
        sesion(-60, Sesion.EstadoSesion.CONFIRMADA); // empieza en una hora

        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.guardar(USUARIO_MENTOR, 1, "x")).getEstado());
    }

    @Test
    void noSePuedeEditarPasadas24HorasDeLaSesion() {
        sesion(25 * 60 + 60, Sesion.EstadoSesion.CONFIRMADA); // terminó hace más de 24 horas

        SesionException e = error(() -> servicio.guardar(USUARIO_MENTOR, 1, "x"));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
        assertTrue(e.getMessage().contains("24 horas"));
        verify(notaRepository, never()).saveAndFlush(any());
    }

    @Test
    void siSePuedeEditarAntesDeCumplirse24Horas() {
        sesion(23 * 60, Sesion.EstadoSesion.CONFIRMADA);

        assertDoesNotThrow(() -> servicio.guardar(USUARIO_MENTOR, 1, "Aún a tiempo"));
    }

    @Test
    void unaSesionCanceladaNoTieneNotas() {
        sesion(120, Sesion.EstadoSesion.CANCELADA);

        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.guardar(USUARIO_MENTOR, 1, "x")).getEstado());
    }

    @Test
    void soloElMentorDeLaSesionEscribe() {
        sesion(120, Sesion.EstadoSesion.CONFIRMADA);

        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.guardar(USUARIO_ESTUDIANTE, 1, "x")).getEstado());
        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.guardar(777, 1, "x")).getEstado());
    }

    @Test
    void laNotaNoPuedeEstarVaciaNiSerMuyLarga() {
        sesion(120, Sesion.EstadoSesion.CONFIRMADA);

        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.guardar(USUARIO_MENTOR, 1, "   ")).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.guardar(USUARIO_MENTOR, 1, null)).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST,
                error(() -> servicio.guardar(USUARIO_MENTOR, 1, "a".repeat(SesionNotaService.MAX_NOTA + 1))).getEstado());
    }

    // ---------- lectura ----------

    @Test
    void elEstudianteLeeLaNotaPeroNoLaPuedeEditar() {
        sesion(120, Sesion.EstadoSesion.CONFIRMADA);
        SesionNota nota = new SesionNota();
        nota.setContenido("Tarea: ejercicios 3 y 4");
        when(notaRepository.findBySesionId(1)).thenReturn(Optional.of(nota));

        NotaResponse r = servicio.obtener(USUARIO_ESTUDIANTE, 1);

        assertEquals("Tarea: ejercicios 3 y 4", r.contenido());
        assertFalse(r.editable());
    }

    @Test
    void elMentorLeeLaNotaYPuedeEditarDentroDelPlazo() {
        sesion(120, Sesion.EstadoSesion.CONFIRMADA);

        NotaResponse r = servicio.obtener(USUARIO_MENTOR, 1);

        assertNull(r.contenido());
        assertTrue(r.editable());
    }

    @Test
    void pasadoElPlazoElMentorSoloLee() {
        sesion(30 * 60, Sesion.EstadoSesion.CONFIRMADA);

        assertFalse(servicio.obtener(USUARIO_MENTOR, 1).editable());
    }

    @Test
    void quienNoParticipaNoPuedeVerLaNota() {
        sesion(120, Sesion.EstadoSesion.CONFIRMADA);

        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.obtener(555, 1)).getEstado());
    }
}
