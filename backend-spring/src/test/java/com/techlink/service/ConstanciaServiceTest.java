package com.techlink.service;

import com.techlink.dto.MentorEstadisticasDtos.Constancia;
import com.techlink.dto.MentorEstadisticasDtos.CursoAtendido;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Curso;
import com.techlink.model.Mentor;
import com.techlink.model.Sesion;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

// Pruebas de HU-27: constancia de horas de mentoría en PDF (datos y archivo).
class ConstanciaServiceTest {

    // "Ahora" para las pruebas: 20/11/2026 a las 12:00.
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 12, 0);

    private InsigniaService insigniaService;
    private MentorRepository mentorRepository;
    private ConstanciaService servicio;
    private final List<Sesion> sesiones = new ArrayList<>();

    @BeforeEach
    void preparar() {
        mentorRepository = Mockito.mock(MentorRepository.class);
        SesionRepository sesionRepository = Mockito.mock(SesionRepository.class);
        insigniaService = Mockito.mock(InsigniaService.class);
        Clock reloj = Clock.fixed(AHORA.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        servicio = new ConstanciaService(mentorRepository, sesionRepository, insigniaService, reloj);

        Mentor mentor = PruebaSesiones.mentor();
        when(mentorRepository.findByUsuarioId(USUARIO_MENTOR)).thenReturn(Optional.of(mentor));
        when(sesionRepository.historialDeMentor(USUARIO_MENTOR)).thenReturn(sesiones);
        when(insigniaService.actividad(ID_MENTOR)).thenReturn(new InsigniaService.Actividad(0, 0));
    }

    private void sesion(int id, LocalDateTime inicio, int minutos, String curso, Sesion.EstadoSesion estado) {
        Sesion s = PruebaSesiones.sesion(id, inicio, minutos, estado);
        if (curso != null) {
            Curso c = new Curso();
            c.setNombre(curso);
            s.setCurso(c);
        }
        sesiones.add(s);
    }

    private void actividad(double horas, int total) {
        when(insigniaService.actividad(ID_MENTOR)).thenReturn(new InsigniaService.Actividad(horas, total));
    }

    @Test
    void agrupaLasHorasPorCursoAtendido() {
        sesion(1, LocalDateTime.of(2026, 11, 3, 10, 0), 60, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        sesion(2, LocalDateTime.of(2026, 11, 4, 10, 0), 90, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        sesion(3, LocalDateTime.of(2026, 10, 15, 10, 0), 60, "Programación", Sesion.EstadoSesion.CONFIRMADA);
        actividad(3.5, 3);

        Constancia c = servicio.datos(USUARIO_MENTOR);

        assertEquals(2, c.cursos().size());
        assertEquals(new CursoAtendido("Bases de Datos", 2, 2.5), c.cursos().get(0));
        assertEquals(new CursoAtendido("Programación", 1, 1.0), c.cursos().get(1));
        assertEquals(3.5, c.horasTotales());
        assertEquals(3, c.sesionesCompletadas());
        assertTrue(c.puedeEmitir());
    }

    @Test
    void elPeriodoVaDeLaPrimeraALaUltimaSesion() {
        sesion(1, LocalDateTime.of(2026, 11, 3, 10, 0), 60, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        sesion(2, LocalDateTime.of(2026, 9, 2, 10, 0), 60, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        sesion(3, LocalDateTime.of(2026, 10, 15, 10, 0), 60, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        actividad(3, 3);

        Constancia c = servicio.datos(USUARIO_MENTOR);

        assertEquals(LocalDate.of(2026, 9, 2), c.desde());
        assertEquals(LocalDate.of(2026, 11, 3), c.hasta());
    }

    @Test
    void lasSesionesSinCursoVanJuntasAlFinal() {
        sesion(1, LocalDateTime.of(2026, 11, 3, 10, 0), 180, null, Sesion.EstadoSesion.CONFIRMADA);
        sesion(2, LocalDateTime.of(2026, 11, 4, 10, 0), 60, "Programación", Sesion.EstadoSesion.CONFIRMADA);
        actividad(4, 2);

        Constancia c = servicio.datos(USUARIO_MENTOR);

        assertEquals("Programación", c.cursos().get(0).curso());
        assertEquals(ConstanciaService.SIN_CURSO, c.cursos().get(1).curso());
        assertEquals(3.0, c.cursos().get(1).horas());
    }

    @Test
    void noCuentanLasCanceladasNiLasQueTodaviaNoTerminan() {
        sesion(1, LocalDateTime.of(2026, 11, 3, 10, 0), 60, "Bases de Datos", Sesion.EstadoSesion.CANCELADA);
        sesion(2, LocalDateTime.of(2026, 11, 20, 11, 30), 60, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        sesion(3, LocalDateTime.of(2026, 11, 25, 10, 0), 60, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);

        Constancia c = servicio.datos(USUARIO_MENTOR);

        assertTrue(c.cursos().isEmpty());
        assertFalse(c.puedeEmitir());
    }

    @Test
    void elFolioYLaFechaDeEmisionSeArmanConElMomentoDeLaEmision() {
        Constancia c = servicio.datos(USUARIO_MENTOR);

        assertEquals(AHORA, c.emitidaEn());
        assertEquals("TL-0005-20261120", c.folio());
        assertEquals("Lucía Ramírez", c.mentor());
        assertEquals("lucia@tecsup.edu.pe", c.correo());
    }

    @Test
    void sinSesionesCompletadasNoSePuedeEmitirElPdf() {
        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> servicio.pdf(USUARIO_MENTOR));

        assertEquals(HttpStatus.CONFLICT, error.getEstado());
    }

    @Test
    void sinPerfilDeMentorEsNoEncontrado() {
        when(mentorRepository.findByUsuarioId(anyInt())).thenReturn(Optional.empty());

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> servicio.pdf(99));

        assertEquals(HttpStatus.NOT_FOUND, error.getEstado());
    }

    @Test
    void elPdfEsUnPdfValidoConElNombreLasHorasYLosCursos() {
        sesion(1, LocalDateTime.of(2026, 11, 3, 10, 0), 90, "Bases de Datos", Sesion.EstadoSesion.CONFIRMADA);
        sesion(2, LocalDateTime.of(2026, 11, 4, 10, 0), 60, "Programación (nivel 1)", Sesion.EstadoSesion.CONFIRMADA);
        actividad(2.5, 2);

        byte[] pdf = servicio.pdf(USUARIO_MENTOR);
        String texto = new String(pdf, StandardCharsets.ISO_8859_1);

        assertTrue(texto.startsWith("%PDF-1.4"));
        assertTrue(texto.trim().endsWith("%%EOF"));
        assertTrue(texto.contains("/Count 1"));
        assertTrue(texto.contains("Lucía Ramírez"), "lleva el nombre del mentor con sus tildes");
        assertTrue(texto.contains("CONSTANCIA DE HORAS DE MENTORÍA"));
        assertTrue(texto.contains("2,5"), "lleva el total de horas");
        assertTrue(texto.contains("Bases de Datos"));
        assertTrue(texto.contains("Programación \\(nivel 1\\)"), "los paréntesis se escapan");
        assertTrue(texto.contains("Folio TL-0005-20261120"));
    }

    @Test
    void conMuchosCursosElPdfContinuaEnOtraPagina() {
        for (int i = 1; i <= 40; i++) {
            sesion(i, LocalDateTime.of(2026, 10, 1, 8, 0).plusDays(i % 20), 60, "Curso numero " + i, Sesion.EstadoSesion.CONFIRMADA);
        }
        actividad(40, 40);

        String texto = new String(servicio.pdf(USUARIO_MENTOR), StandardCharsets.ISO_8859_1);

        assertTrue(texto.contains("/Count 2") || texto.contains("/Count 3"));
        assertTrue(texto.contains("Página 1 de"));
        assertTrue(texto.contains("Curso numero 40"));
    }

    @Test
    void lasHorasSeEscribenConComaYSinCerosSobrantes() {
        assertEquals("12", ConstanciaPdf.horas(12.0));
        assertEquals("12,5", ConstanciaPdf.horas(12.5));
        assertEquals("12,25", ConstanciaPdf.horas(12.25));
        assertEquals("0", ConstanciaPdf.horas(0));
    }
}
