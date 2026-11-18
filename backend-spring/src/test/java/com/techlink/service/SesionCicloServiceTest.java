package com.techlink.service;

import com.techlink.exception.SesionException;
import com.techlink.model.Curso;
import com.techlink.model.MentorDisponibilidad;
import com.techlink.model.Sesion;
import com.techlink.repository.CursoRepository;
import com.techlink.repository.MentorDisponibilidadRepository;
import com.techlink.repository.SesionMentorBloqueoRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static com.techlink.service.PruebaSesiones.ID_MENTOR;
import static com.techlink.service.PruebaSesiones.USUARIO_ESTUDIANTE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-19 (cancelar y reprogramar) y del curso de la sesión (HU-20).
class SesionCicloServiceTest {

    private SesionRepository sesionRepository;
    private SesionMentorBloqueoRepository bloqueoRepository;
    private MentorDisponibilidadRepository disponibilidadRepository;
    private CursoRepository cursoRepository;
    private ApplicationEventPublisher eventos;
    private SesionCicloService servicio;

    @BeforeEach
    void preparar() {
        sesionRepository = Mockito.mock(SesionRepository.class);
        bloqueoRepository = Mockito.mock(SesionMentorBloqueoRepository.class);
        disponibilidadRepository = Mockito.mock(MentorDisponibilidadRepository.class);
        cursoRepository = Mockito.mock(CursoRepository.class);
        eventos = Mockito.mock(ApplicationEventPublisher.class);
        SesionService sesionService = new SesionService(sesionRepository, null, null, disponibilidadRepository,
                bloqueoRepository, null, eventos);
        servicio = new SesionCicloService(sesionRepository, bloqueoRepository, disponibilidadRepository,
                cursoRepository, sesionService, eventos);
        when(sesionRepository.save(any(Sesion.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Sesion sesionEn(long horas, Sesion.EstadoSesion estado) {
        Sesion s = PruebaSesiones.sesion(1, LocalDateTime.now().plusHours(horas), 60, estado);
        when(sesionRepository.findById(1)).thenReturn(Optional.of(s));
        return s;
    }

    private static SesionException error(Runnable accion) {
        return assertThrows(SesionException.class, accion::run);
    }

    // ---------- cancelar ----------

    @Test
    void cancelaUnaSesionConMasDeDosHorasDeAnticipacion() {
        Sesion s = sesionEn(5, Sesion.EstadoSesion.CONFIRMADA);

        Sesion r = servicio.cancelar(USUARIO_ESTUDIANTE, 1);

        assertEquals(Sesion.EstadoSesion.CANCELADA, r.getEstado());
        ArgumentCaptor<SesionModificadaEvent> evento = ArgumentCaptor.forClass(SesionModificadaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertEquals(SesionModificadaEvent.Tipo.CANCELADA, evento.getValue().tipo());
        assertEquals("lucia@tecsup.edu.pe", evento.getValue().correoMentor());
        assertEquals(s.getFecha(), evento.getValue().fechaAnterior());
    }

    @Test
    void noCancelaConMenosDeDosHoras() {
        sesionEn(1, Sesion.EstadoSesion.CONFIRMADA);

        SesionException e = error(() -> servicio.cancelar(USUARIO_ESTUDIANTE, 1));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
        assertTrue(e.getMessage().contains("2 horas"));
        verify(sesionRepository, never()).save(any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void noCancelaUnaSesionQueYaEmpezo() {
        sesionEn(-1, Sesion.EstadoSesion.CONFIRMADA);

        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.cancelar(USUARIO_ESTUDIANTE, 1)).getEstado());
    }

    @Test
    void noCancelaDosVeces() {
        sesionEn(5, Sesion.EstadoSesion.CANCELADA);

        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.cancelar(USUARIO_ESTUDIANTE, 1)).getEstado());
    }

    @Test
    void laSesionDeOtroEstudianteSeTrataComoInexistente() {
        sesionEn(5, Sesion.EstadoSesion.CONFIRMADA);

        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.cancelar(999, 1)).getEstado());
    }

    @Test
    void unaSesionQueNoExisteDaNotFound() {
        when(sesionRepository.findById(77)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.cancelar(USUARIO_ESTUDIANTE, 77)).getEstado());
    }

    // ---------- reprogramar ----------

    private LocalDateTime nuevoInicio() {
        return LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    private void mentorConDisponibilidad(LocalDateTime nuevo) {
        MentorDisponibilidad d = new MentorDisponibilidad();
        d.setId(9);
        d.setMentor(PruebaSesiones.mentor());
        d.setActivo(true);
        d.setDiaSemana(nuevo.getDayOfWeek().getValue());
        d.setHoraInicio(LocalTime.of(8, 0));
        d.setHoraFin(LocalTime.of(14, 0));
        when(bloqueoRepository.buscarMentorConBloqueo(ID_MENTOR)).thenReturn(Optional.of(PruebaSesiones.mentor()));
        when(disponibilidadRepository.findById(9)).thenReturn(Optional.of(d));
    }

    @Test
    void reprogramaALaNuevaFechaYAvisa() {
        Sesion s = sesionEn(5, Sesion.EstadoSesion.CONFIRMADA);
        LocalDate anterior = s.getFecha();
        LocalTime inicioAnterior = s.getHoraInicio();
        LocalDateTime nuevo = nuevoInicio();
        mentorConDisponibilidad(nuevo);

        Sesion r = servicio.reprogramar(USUARIO_ESTUDIANTE, 1, 9, nuevo.toLocalDate(),
                LocalTime.of(10, 0), LocalTime.of(11, 0));

        assertEquals(nuevo.toLocalDate(), r.getFecha());
        assertEquals(LocalTime.of(10, 0), r.getHoraInicio());
        assertEquals(Sesion.EstadoSesion.CONFIRMADA, r.getEstado());
        ArgumentCaptor<SesionModificadaEvent> evento = ArgumentCaptor.forClass(SesionModificadaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertEquals(SesionModificadaEvent.Tipo.REPROGRAMADA, evento.getValue().tipo());
        assertEquals(anterior, evento.getValue().fechaAnterior());
        assertEquals(inicioAnterior, evento.getValue().horaInicioAnterior());
    }

    @Test
    void noReprogramaConMenosDeDosHoras() {
        sesionEn(1, Sesion.EstadoSesion.CONFIRMADA);
        LocalDateTime nuevo = nuevoInicio();
        mentorConDisponibilidad(nuevo);

        SesionException e = error(() -> servicio.reprogramar(USUARIO_ESTUDIANTE, 1, 9, nuevo.toLocalDate(),
                LocalTime.of(10, 0), LocalTime.of(11, 0)));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
    }

    @Test
    void noReprogramaAUnHorarioOcupado() {
        sesionEn(5, Sesion.EstadoSesion.CONFIRMADA);
        LocalDateTime nuevo = nuevoInicio();
        mentorConDisponibilidad(nuevo);
        when(sesionRepository.contarSuperpuestasExcluyendo(eq(ID_MENTOR), any(), any(), any(), eq(1))).thenReturn(1L);

        SesionException e = error(() -> servicio.reprogramar(USUARIO_ESTUDIANTE, 1, 9, nuevo.toLocalDate(),
                LocalTime.of(10, 0), LocalTime.of(11, 0)));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
        verify(sesionRepository, never()).save(any());
    }

    @Test
    void noReprogramaAUnDiaEnQueElMentorNoAtiende() {
        sesionEn(5, Sesion.EstadoSesion.CONFIRMADA);
        LocalDateTime nuevo = nuevoInicio();
        mentorConDisponibilidad(nuevo);

        SesionException e = error(() -> servicio.reprogramar(USUARIO_ESTUDIANTE, 1, 9, nuevo.toLocalDate().plusDays(1),
                LocalTime.of(10, 0), LocalTime.of(11, 0)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getEstado());
    }

    @Test
    void noReprogramaAUnaFechaPasada() {
        sesionEn(5, Sesion.EstadoSesion.CONFIRMADA);
        LocalDateTime pasado = LocalDateTime.now().minusDays(2);
        mentorConDisponibilidad(pasado);

        SesionException e = error(() -> servicio.reprogramar(USUARIO_ESTUDIANTE, 1, 9, pasado.toLocalDate(),
                LocalTime.of(10, 0), LocalTime.of(11, 0)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getEstado());
    }

    @Test
    void reprogramarExigeElegirUnHorario() {
        SesionException e = error(() -> servicio.reprogramar(USUARIO_ESTUDIANTE, 1, null, LocalDate.now(),
                LocalTime.of(10, 0), LocalTime.of(11, 0)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getEstado());
    }

    @Test
    void unaSesionCanceladaNoSePuedeReprogramar() {
        sesionEn(5, Sesion.EstadoSesion.CANCELADA);
        LocalDateTime nuevo = nuevoInicio();
        mentorConDisponibilidad(nuevo);

        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.reprogramar(USUARIO_ESTUDIANTE, 1, 9,
                nuevo.toLocalDate(), LocalTime.of(10, 0), LocalTime.of(11, 0))).getEstado());
    }

    // ---------- curso de la sesión ----------

    @Test
    void asignaElCursoElegido() {
        Sesion s = sesionEn(5, Sesion.EstadoSesion.CONFIRMADA);
        Curso bd = new Curso();
        bd.setId(4);
        bd.setNombre("Base de Datos");
        bd.setActivo(true);
        when(cursoRepository.findById(4)).thenReturn(Optional.of(bd));

        servicio.asignarCurso(1, 4);

        assertSame(bd, s.getCurso());
        verify(sesionRepository).save(s);
    }

    @Test
    void sinCursoNoHaceNada() {
        servicio.asignarCurso(1, null);

        verify(sesionRepository, never()).findById(anyInt());
    }

    @Test
    void unCursoInactivoOInexistenteEsInvalido() {
        sesionEn(5, Sesion.EstadoSesion.CONFIRMADA);
        Curso inactivo = new Curso();
        inactivo.setId(99);
        inactivo.setActivo(false);
        when(cursoRepository.findById(99)).thenReturn(Optional.of(inactivo));

        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.asignarCurso(1, 99)).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.asignarCurso(1, 123)).getEstado());
    }
}
