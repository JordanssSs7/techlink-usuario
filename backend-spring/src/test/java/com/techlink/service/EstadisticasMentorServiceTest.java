package com.techlink.service;

import com.techlink.dto.MentorEstadisticasDtos.Estadisticas;
import com.techlink.dto.MentorEstadisticasDtos.Mes;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Mentor;
import com.techlink.model.Sesion;
import com.techlink.repository.CalificacionRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.techlink.service.PruebaSesiones.ID_MENTOR;
import static com.techlink.service.PruebaSesiones.USUARIO_MENTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

// Pruebas de HU-28: estadísticas del mentor mes a mes (sesiones, horas y promedio de estrellas).
class EstadisticasMentorServiceTest {

    // "Ahora" para las pruebas: 20/11/2026 a las 12:00.
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 12, 0);

    private SesionRepository sesionRepository;
    private CalificacionRepository calificacionRepository;
    private InsigniaService insigniaService;
    private EstadisticasMentorService servicio;
    private final List<Sesion> sesiones = new ArrayList<>();
    private final List<Object[]> calificaciones = new ArrayList<>();

    @BeforeEach
    void preparar() {
        MentorRepository mentorRepository = Mockito.mock(MentorRepository.class);
        sesionRepository = Mockito.mock(SesionRepository.class);
        calificacionRepository = Mockito.mock(CalificacionRepository.class);
        insigniaService = Mockito.mock(InsigniaService.class);
        Clock reloj = Clock.fixed(AHORA.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        servicio = new EstadisticasMentorService(mentorRepository, sesionRepository, calificacionRepository, insigniaService, reloj);

        Mentor mentor = PruebaSesiones.mentor();
        when(mentorRepository.findByUsuarioId(USUARIO_MENTOR)).thenReturn(Optional.of(mentor));
        when(sesionRepository.historialDeMentor(USUARIO_MENTOR)).thenReturn(sesiones);
        when(calificacionRepository.fechasYEstrellasDeMentor(ID_MENTOR)).thenReturn(calificaciones);
        when(insigniaService.actividad(ID_MENTOR)).thenReturn(new InsigniaService.Actividad(0, 0));
        CalificacionRepository.Resumen resumen = Mockito.mock(CalificacionRepository.Resumen.class);
        when(resumen.getTotal()).thenReturn(0L);
        when(calificacionRepository.resumenDeMentor(ID_MENTOR)).thenReturn(resumen);
    }

    private void sesion(int id, LocalDateTime inicio, int minutos, Sesion.EstadoSesion estado) {
        sesiones.add(PruebaSesiones.sesion(id, inicio, minutos, estado));
    }

    private void calificacion(LocalDate fechaSesion, int estrellas) {
        calificaciones.add(new Object[]{fechaSesion, estrellas});
    }

    private static Mes mes(Estadisticas e, String clave) {
        return e.meses().stream().filter(m -> m.mes().equals(clave)).findFirst().orElseThrow();
    }

    @Test
    void devuelveLosUltimos12MesesEnCeroYEnOrden() {
        Estadisticas e = servicio.estadisticas(USUARIO_MENTOR, null);

        assertEquals(12, e.meses().size());
        assertEquals("2025-12", e.meses().get(0).mes());
        assertEquals("2026-11", e.meses().get(11).mes());
        assertEquals(0, mes(e, "2026-05").sesiones());
        assertEquals(0.0, mes(e, "2026-05").horas());
        assertNull(mes(e, "2026-05").promedio());
        assertEquals(0, mes(e, "2026-05").calificaciones());
    }

    @Test
    void sumaSesionesYHorasPorMes() {
        sesion(1, LocalDateTime.of(2026, 11, 3, 10, 0), 90, Sesion.EstadoSesion.CONFIRMADA);
        sesion(2, LocalDateTime.of(2026, 11, 10, 15, 0), 60, Sesion.EstadoSesion.CONFIRMADA);
        sesion(3, LocalDateTime.of(2026, 10, 20, 9, 0), 120, Sesion.EstadoSesion.CONFIRMADA);

        Estadisticas e = servicio.estadisticas(USUARIO_MENTOR, null);

        assertEquals(2, mes(e, "2026-11").sesiones());
        assertEquals(2.5, mes(e, "2026-11").horas());
        assertEquals(1, mes(e, "2026-10").sesiones());
        assertEquals(2.0, mes(e, "2026-10").horas());
    }

    @Test
    void lasSesionesCanceladasNoCuentan() {
        sesion(1, LocalDateTime.of(2026, 11, 3, 10, 0), 60, Sesion.EstadoSesion.CANCELADA);

        Estadisticas e = servicio.estadisticas(USUARIO_MENTOR, null);

        assertEquals(0, mes(e, "2026-11").sesiones());
        assertEquals(0, e.resumen().sesionesProximas());
    }

    @Test
    void unaSesionQueTodaviaNoTerminaCuentaComoProximaYNoComoHoraDictada() {
        // Hoy a las 11:30 a 12:30 (aún no termina a las 12:00) y otra mañana.
        sesion(1, LocalDateTime.of(2026, 11, 20, 11, 30), 60, Sesion.EstadoSesion.CONFIRMADA);
        sesion(2, LocalDateTime.of(2026, 11, 21, 10, 0), 60, Sesion.EstadoSesion.CONFIRMADA);
        // Una que terminó justo a las 12:00 sí cuenta (igual que en las insignias).
        sesion(3, LocalDateTime.of(2026, 11, 20, 11, 0), 60, Sesion.EstadoSesion.CONFIRMADA);

        Estadisticas e = servicio.estadisticas(USUARIO_MENTOR, null);

        assertEquals(1, mes(e, "2026-11").sesiones());
        assertEquals(1.0, mes(e, "2026-11").horas());
        assertEquals(2, e.resumen().sesionesProximas());
    }

    @Test
    void elPromedioDelMesSaleDeLasCalificacionesDeEseMes() {
        calificacion(LocalDate.of(2026, 11, 3), 5);
        calificacion(LocalDate.of(2026, 11, 10), 4);
        calificacion(LocalDate.of(2026, 11, 11), 4);
        calificacion(LocalDate.of(2026, 10, 2), 3);

        Estadisticas e = servicio.estadisticas(USUARIO_MENTOR, null);

        assertEquals(new BigDecimal("4.33"), mes(e, "2026-11").promedio());
        assertEquals(3, mes(e, "2026-11").calificaciones());
        assertEquals(new BigDecimal("3.00"), mes(e, "2026-10").promedio());
        assertNull(mes(e, "2026-09").promedio());
    }

    @Test
    void lasCalificacionesFueraDeLaVentanaSeIgnoranEnElDetalle() {
        calificacion(LocalDate.of(2024, 1, 5), 1);
        sesion(1, LocalDateTime.of(2024, 1, 5, 10, 0), 60, Sesion.EstadoSesion.CONFIRMADA);

        Estadisticas e = servicio.estadisticas(USUARIO_MENTOR, null);

        assertEquals(0, e.meses().stream().mapToInt(Mes::sesiones).sum());
        assertEquals(0, e.meses().stream().mapToInt(Mes::calificaciones).sum());
    }

    @Test
    void laCantidadDeMesesSeAjustaEntre1Y24() {
        assertEquals(1, servicio.estadisticas(USUARIO_MENTOR, 0).meses().size());
        assertEquals(1, servicio.estadisticas(USUARIO_MENTOR, -5).meses().size());
        assertEquals(24, servicio.estadisticas(USUARIO_MENTOR, 100).meses().size());
        assertEquals(6, servicio.estadisticas(USUARIO_MENTOR, 6).meses().size());
        assertEquals("2026-11", servicio.estadisticas(USUARIO_MENTOR, 1).meses().get(0).mes());
    }

    @Test
    void losTotalesSalenDelMismoCalculoQueElPerfilPublico() {
        when(insigniaService.actividad(ID_MENTOR)).thenReturn(new InsigniaService.Actividad(12.5, 9));
        CalificacionRepository.Resumen resumen = Mockito.mock(CalificacionRepository.Resumen.class);
        when(resumen.getTotal()).thenReturn(4L);
        when(resumen.getPromedio()).thenReturn(4.6666);
        when(calificacionRepository.resumenDeMentor(ID_MENTOR)).thenReturn(resumen);

        Estadisticas e = servicio.estadisticas(USUARIO_MENTOR, null);

        assertEquals(12.5, e.resumen().horasTotales());
        assertEquals(9, e.resumen().sesionesCompletadas());
        assertEquals(new BigDecimal("4.67"), e.resumen().promedio());
        assertEquals(4, e.resumen().totalCalificaciones());
    }

    @Test
    void sinCalificacionesElPromedioEsNulo() {
        Estadisticas e = servicio.estadisticas(USUARIO_MENTOR, null);

        assertNull(e.resumen().promedio());
        assertEquals(0, e.resumen().totalCalificaciones());
    }

    @Test
    void siNoTienePerfilDeMentorEsNoEncontrado() {
        MentorRepository vacio = Mockito.mock(MentorRepository.class);
        when(vacio.findByUsuarioId(anyInt())).thenReturn(Optional.empty());
        EstadisticasMentorService sinPerfil = new EstadisticasMentorService(vacio, sesionRepository, calificacionRepository, insigniaService);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> sinPerfil.estadisticas(99, null));

        assertEquals(HttpStatus.NOT_FOUND, error.getEstado());
    }
}
