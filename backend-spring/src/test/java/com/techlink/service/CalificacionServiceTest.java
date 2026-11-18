package com.techlink.service;

import com.techlink.dto.CalificacionResponse;
import com.techlink.exception.SesionException;
import com.techlink.model.Calificacion;
import com.techlink.model.Mentor;
import com.techlink.model.Sesion;
import com.techlink.repository.CalificacionRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.techlink.service.PruebaSesiones.USUARIO_ESTUDIANTE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-09: calificar una sesión y recalcular la reputación del mentor.
class CalificacionServiceTest {

    private SesionRepository sesionRepository;
    private CalificacionRepository calificacionRepository;
    private MentorRepository mentorRepository;
    private ApplicationEventPublisher eventos;
    private CalificacionService servicio;

    @BeforeEach
    void preparar() {
        sesionRepository = Mockito.mock(SesionRepository.class);
        calificacionRepository = Mockito.mock(CalificacionRepository.class);
        mentorRepository = Mockito.mock(MentorRepository.class);
        eventos = Mockito.mock(ApplicationEventPublisher.class);
        servicio = new CalificacionService(sesionRepository, calificacionRepository, mentorRepository, eventos);
        when(calificacionRepository.findBySesionId(1)).thenReturn(Optional.empty());
        when(mentorRepository.save(any(Mentor.class))).thenAnswer(i -> i.getArgument(0));
        resumen(4.5, 2L);
    }

    private void resumen(Double promedio, Long total) {
        CalificacionRepository.Resumen r = Mockito.mock(CalificacionRepository.Resumen.class);
        when(r.getPromedio()).thenReturn(promedio);
        when(r.getTotal()).thenReturn(total);
        when(calificacionRepository.resumenDeMentor(PruebaSesiones.ID_MENTOR)).thenReturn(r);
    }

    // Una sesión que empezó hace "horasAtras" horas y duró una hora.
    private Sesion sesion(long horasAtras, Sesion.EstadoSesion estado) {
        Sesion s = PruebaSesiones.sesion(1, LocalDateTime.now().minusHours(horasAtras), 30, estado);
        when(sesionRepository.findById(1)).thenReturn(Optional.of(s));
        return s;
    }

    private static SesionException error(Runnable accion) {
        return assertThrows(SesionException.class, accion::run);
    }

    @Test
    void calificaUnaSesionTerminadaYRecalculaLaReputacion() {
        sesion(3, Sesion.EstadoSesion.CONFIRMADA);

        CalificacionResponse r = servicio.calificar(USUARIO_ESTUDIANTE, 1, 5, "  Muy clara la explicación  ");

        ArgumentCaptor<Calificacion> guardada = ArgumentCaptor.forClass(Calificacion.class);
        verify(calificacionRepository).save(guardada.capture());
        assertEquals(5, guardada.getValue().getEstrellas());
        assertEquals("Muy clara la explicación", guardada.getValue().getComentario());
        assertEquals(new BigDecimal("4.50"), r.reputacionMentor());
        assertEquals(2, r.totalCalificacionesMentor());
    }

    @Test
    void avisaQueHayUnaNuevaCalificacionParaRevisarLasInsignias() {
        sesion(3, Sesion.EstadoSesion.CONFIRMADA);

        servicio.calificar(USUARIO_ESTUDIANTE, 1, 5, null);

        verify(eventos).publishEvent(new InsigniaProgramador.CalificacionRegistrada(PruebaSesiones.ID_MENTOR));
    }

    @Test
    void elComentarioEsOpcional() {
        sesion(3, Sesion.EstadoSesion.CONFIRMADA);

        CalificacionResponse r = servicio.calificar(USUARIO_ESTUDIANTE, 1, 4, "   ");

        assertNull(r.comentario());
    }

    @Test
    void laReputacionSeRedondeaADosDecimales() {
        sesion(3, Sesion.EstadoSesion.CONFIRMADA);
        resumen(4.3333333, 3L);

        assertEquals(new BigDecimal("4.33"), servicio.calificar(USUARIO_ESTUDIANTE, 1, 4, null).reputacionMentor());
    }

    @Test
    void lasEstrellasDebenSerDe1a5() {
        for (Integer invalido : new Integer[]{null, 0, 6, -1}) {
            assertEquals(HttpStatus.BAD_REQUEST,
                    error(() -> servicio.calificar(USUARIO_ESTUDIANTE, 1, invalido, null)).getEstado());
        }
        verify(calificacionRepository, never()).save(any());
    }

    @Test
    void elComentarioNoPuedeSuperar500Caracteres() {
        sesion(3, Sesion.EstadoSesion.CONFIRMADA);

        assertEquals(HttpStatus.BAD_REQUEST,
                error(() -> servicio.calificar(USUARIO_ESTUDIANTE, 1, 5, "a".repeat(501))).getEstado());
    }

    @Test
    void noSePuedeCalificarAntesDeQueTermine() {
        // Empezó hace 10 minutos y dura 30: todavía no termina.
        Sesion s = PruebaSesiones.sesion(1, LocalDateTime.now().minusMinutes(10), 30, Sesion.EstadoSesion.CONFIRMADA);
        when(sesionRepository.findById(1)).thenReturn(Optional.of(s));

        SesionException e = error(() -> servicio.calificar(USUARIO_ESTUDIANTE, 1, 5, null));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
        verify(calificacionRepository, never()).save(any());
    }

    @Test
    void noSeCalificaUnaSesionCancelada() {
        sesion(3, Sesion.EstadoSesion.CANCELADA);

        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.calificar(USUARIO_ESTUDIANTE, 1, 5, null)).getEstado());
    }

    @Test
    void noSeCalificaDosVeces() {
        sesion(3, Sesion.EstadoSesion.CONFIRMADA);
        when(calificacionRepository.findBySesionId(1)).thenReturn(Optional.of(new Calificacion()));

        SesionException e = error(() -> servicio.calificar(USUARIO_ESTUDIANTE, 1, 5, null));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
        assertTrue(e.getMessage().contains("Ya calificaste"));
    }

    @Test
    void noSePuedeCalificarLaSesionDeOtroEstudiante() {
        sesion(3, Sesion.EstadoSesion.CONFIRMADA);

        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.calificar(999, 1, 5, null)).getEstado());
    }
}
