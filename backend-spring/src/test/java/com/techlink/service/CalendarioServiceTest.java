package com.techlink.service;

import com.techlink.dto.EventoCalendarioResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Charla;
import com.techlink.model.Sesion;
import com.techlink.repository.CharlaRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static com.techlink.service.PruebaSesiones.USUARIO_ESTUDIANTE;
import static com.techlink.service.PruebaSesiones.USUARIO_MENTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-26: calendario con las sesiones y las charlas del usuario.
class CalendarioServiceTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 11, 16);
    private static final LocalDate HASTA = LocalDate.of(2026, 11, 22);

    private SesionRepository sesionRepository;
    private CharlaRepository charlaRepository;
    private CalendarioService servicio;

    @BeforeEach
    void preparar() {
        sesionRepository = Mockito.mock(SesionRepository.class);
        charlaRepository = Mockito.mock(CharlaRepository.class);
        servicio = new CalendarioService(sesionRepository, charlaRepository);
    }

    private static Sesion sesion(int id, int dia, int hora) {
        return PruebaSesiones.sesion(id, LocalDateTime.of(2026, 11, dia, hora, 0), 60, Sesion.EstadoSesion.CONFIRMADA);
    }

    private static Charla charla(int id, int dia, int hora) {
        Charla c = new Charla();
        c.setId(id);
        c.setNombre("Charla " + id);
        c.setTematica("Tema " + id);
        c.setFecha(LocalDate.of(2026, 11, dia));
        c.setHoraInicio(LocalTime.of(hora, 0));
        c.setHoraFin(LocalTime.of(hora + 1, 0));
        c.setEnlace("https://meet.test/c" + id);
        return c;
    }

    private static ReglaNegocioException error(Runnable accion) {
        return assertThrows(ReglaNegocioException.class, accion::run);
    }

    @Test
    void juntaSesionesYCharlasOrdenadasPorFechaYHora() {
        when(sesionRepository.confirmadasDelUsuarioEntre(USUARIO_ESTUDIANTE, DESDE, HASTA))
                .thenReturn(List.of(sesion(1, 18, 15), sesion(2, 20, 9)));
        when(charlaRepository.inscritasEntre(USUARIO_ESTUDIANTE, DESDE, HASTA))
                .thenReturn(List.of(charla(5, 18, 10), charla(6, 20, 8)));

        List<EventoCalendarioResponse> eventos = servicio.eventos(USUARIO_ESTUDIANTE, "ESTUDIANTE", DESDE, HASTA);

        assertEquals(List.of("CHARLA", "SESION", "CHARLA", "SESION"), eventos.stream().map(EventoCalendarioResponse::tipo).toList());
        assertEquals(List.of(5, 1, 6, 2), eventos.stream().map(EventoCalendarioResponse::id).toList());
    }

    @Test
    void laSesionIndicaConQuienEsSegunElRol() {
        when(sesionRepository.confirmadasDelUsuarioEntre(USUARIO_ESTUDIANTE, DESDE, HASTA)).thenReturn(List.of(sesion(1, 18, 15)));
        when(sesionRepository.confirmadasDelUsuarioEntre(USUARIO_MENTOR, DESDE, HASTA)).thenReturn(List.of(sesion(1, 18, 15)));

        EventoCalendarioResponse comoEstudiante = servicio.eventos(USUARIO_ESTUDIANTE, "ESTUDIANTE", DESDE, HASTA).get(0);
        EventoCalendarioResponse comoMentor = servicio.eventos(USUARIO_MENTOR, "MENTOR", DESDE, HASTA).get(0);

        assertEquals("Con Lucía Ramírez", comoEstudiante.detalle());
        assertEquals("Con Ana Quispe", comoMentor.detalle());
        assertEquals("Mentoría", comoEstudiante.titulo());
        assertEquals("SESION", comoEstudiante.tipo());
    }

    @Test
    void elMentorSoloVeSesionesNoCharlas() {
        when(sesionRepository.confirmadasDelUsuarioEntre(USUARIO_MENTOR, DESDE, HASTA)).thenReturn(List.of(sesion(1, 18, 15)));

        List<EventoCalendarioResponse> eventos = servicio.eventos(USUARIO_MENTOR, "MENTOR", DESDE, HASTA);

        assertEquals(1, eventos.size());
        verify(charlaRepository, never()).inscritasEntre(any(), any(), any());
    }

    @Test
    void laCharlaMuestraSuTematicaYSuEnlace() {
        when(charlaRepository.inscritasEntre(USUARIO_ESTUDIANTE, DESDE, HASTA)).thenReturn(List.of(charla(5, 18, 10)));

        EventoCalendarioResponse e = servicio.eventos(USUARIO_ESTUDIANTE, "ESTUDIANTE", DESDE, HASTA).get(0);

        assertEquals("Tema 5", e.detalle());
        assertEquals("https://meet.test/c5", e.enlace());
    }

    @Test
    void rechazaFechasFaltantesInvertidasOUnRangoMuyLargo() {
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.eventos(1, "ESTUDIANTE", null, HASTA)).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.eventos(1, "ESTUDIANTE", HASTA, DESDE)).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST,
                error(() -> servicio.eventos(1, "ESTUDIANTE", DESDE, DESDE.plusDays(CalendarioService.MAX_DIAS + 1))).getEstado());
    }
}
