package com.techlink.service;

import com.techlink.model.Insignia;
import com.techlink.model.Mentor;
import com.techlink.repository.CalificacionRepository;
import com.techlink.repository.InsigniaRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-15: las insignias se otorgan solas al alcanzar un umbral de horas o de promedio.
class InsigniaServiceTest {

    private InsigniaRepository insigniaRepository;
    private MentorRepository mentorRepository;
    private SesionRepository sesionRepository;
    private CalificacionRepository calificacionRepository;
    private InsigniaService servicio;
    private Mentor mentor;
    private final List<Object[]> sesiones = new ArrayList<>();

    @BeforeEach
    void preparar() {
        insigniaRepository = Mockito.mock(InsigniaRepository.class);
        mentorRepository = Mockito.mock(MentorRepository.class);
        sesionRepository = Mockito.mock(SesionRepository.class);
        calificacionRepository = Mockito.mock(CalificacionRepository.class);
        servicio = new InsigniaService(insigniaRepository, mentorRepository, sesionRepository, calificacionRepository);

        mentor = PruebaSesiones.mentor();
        when(mentorRepository.findById(PruebaSesiones.ID_MENTOR)).thenReturn(Optional.of(mentor));
        when(mentorRepository.findAll()).thenReturn(List.of(mentor));
        when(sesionRepository.horariosConfirmadosDeMentor(PruebaSesiones.ID_MENTOR)).thenReturn(sesiones);
        when(insigniaRepository.findAllByOrderByIdAsc()).thenReturn(List.of(
                insignia(1, "PRIMERA_MENTORIA", Insignia.Tipo.HORAS, 1, 0),
                insignia(2, "MENTOR_CONSTANTE", Insignia.Tipo.HORAS, 10, 0),
                insignia(4, "MENTOR_ESTRELLA", Insignia.Tipo.PROMEDIO, 4.5, 3)));
        calificaciones(null, 0);
    }

    private static Insignia insignia(int id, String codigo, Insignia.Tipo tipo, double umbral, int minimo) {
        Insignia i = new Insignia();
        i.setId(id);
        i.setCodigo(codigo);
        i.setNombre(codigo);
        i.setDescripcion("d");
        i.setTipo(tipo);
        i.setUmbral(BigDecimal.valueOf(umbral));
        i.setMinimoCalificaciones(minimo);
        return i;
    }

    private void calificaciones(Double promedio, long total) {
        CalificacionRepository.Resumen r = Mockito.mock(CalificacionRepository.Resumen.class);
        when(r.getPromedio()).thenReturn(promedio);
        when(r.getTotal()).thenReturn(total);
        when(calificacionRepository.resumenDeMentor(PruebaSesiones.ID_MENTOR)).thenReturn(r);
    }

    // Una sesión que terminó hace "diasAtras" días y duró "minutos".
    private void sesionTerminada(int diasAtras, int minutos) {
        LocalDate fecha = LocalDate.now().minusDays(diasAtras);
        sesiones.add(new Object[]{fecha, LocalTime.of(9, 0), LocalTime.of(9, 0).plusMinutes(minutos)});
    }

    private List<String> codigos(List<Insignia> lista) {
        return lista.stream().map(Insignia::getCodigo).toList();
    }

    @Test
    void sinSesionesNiCalificacionesNoGanaNada() {
        assertTrue(servicio.evaluar(PruebaSesiones.ID_MENTOR).isEmpty());
        verify(mentorRepository, never()).save(any());
    }

    @Test
    void lasHorasSoloCuentanSiLaSesionYaTermino() {
        sesionTerminada(2, 60);
        sesiones.add(new Object[]{LocalDate.now().plusDays(3), LocalTime.of(9, 0), LocalTime.of(11, 0)});

        InsigniaService.Actividad a = servicio.actividad(PruebaSesiones.ID_MENTOR);

        assertEquals(1.0, a.horas());
        assertEquals(1, a.sesiones());
    }

    @Test
    void laPrimeraHoraDaLaPrimeraInsignia() {
        sesionTerminada(1, 60);

        List<Insignia> nuevas = servicio.evaluar(PruebaSesiones.ID_MENTOR);

        assertEquals(List.of("PRIMERA_MENTORIA"), codigos(nuevas));
        assertEquals(1, mentor.getInsignias().size());
        verify(mentorRepository).save(mentor);
    }

    @Test
    void diezHorasDanLaInsigniaConstanteYNoAntes() {
        for (int d = 1; d <= 9; d++) {
            sesionTerminada(d, 60);
        }
        sesionTerminada(10, 30); // 9.5 horas
        assertEquals(List.of("PRIMERA_MENTORIA"), codigos(servicio.evaluar(PruebaSesiones.ID_MENTOR)));

        sesionTerminada(11, 30); // 10 horas justas
        assertEquals(List.of("MENTOR_CONSTANTE"), codigos(servicio.evaluar(PruebaSesiones.ID_MENTOR)));
    }

    @Test
    void elPromedioAltoNecesitaAlMenos3Calificaciones() {
        calificaciones(5.0, 2);
        assertTrue(servicio.evaluar(PruebaSesiones.ID_MENTOR).isEmpty());

        calificaciones(4.5, 3);
        assertEquals(List.of("MENTOR_ESTRELLA"), codigos(servicio.evaluar(PruebaSesiones.ID_MENTOR)));
    }

    @Test
    void unPromedioDe44NoAlcanza() {
        calificaciones(4.4, 10);

        assertTrue(servicio.evaluar(PruebaSesiones.ID_MENTOR).isEmpty());
    }

    @Test
    void noOtorgaDosVecesLaMismaInsignia() {
        sesionTerminada(1, 60);
        servicio.evaluar(PruebaSesiones.ID_MENTOR);

        assertTrue(servicio.evaluar(PruebaSesiones.ID_MENTOR).isEmpty());
        assertEquals(1, mentor.getInsignias().size());
    }

    @Test
    void unMentorQueNoExisteNoHaceNada() {
        when(mentorRepository.findById(99)).thenReturn(Optional.empty());

        assertTrue(servicio.evaluar(99).isEmpty());
    }

    @Test
    void evaluarTodosCuentaLasInsigniasNuevas() {
        sesionTerminada(1, 60);

        assertEquals(1, servicio.evaluarTodos());
        assertEquals(0, servicio.evaluarTodos());
    }

    @Test
    void elTiempoDeLaSesionQueTerminaAhoraMismoYaCuenta() {
        LocalDateTime hace = LocalDateTime.now().minusMinutes(1);
        // Terminó hace un minuto: empezó hace 61 minutos.
        sesiones.add(new Object[]{hace.toLocalDate(), hace.minusMinutes(60).toLocalTime(), hace.toLocalTime()});

        // Si cruza la medianoche el cálculo de la prueba no aplica; en ese caso no se evalúa.
        if (hace.toLocalDate().equals(hace.minusMinutes(60).toLocalDate())) {
            assertEquals(1, servicio.actividad(PruebaSesiones.ID_MENTOR).sesiones());
        }
    }
}
