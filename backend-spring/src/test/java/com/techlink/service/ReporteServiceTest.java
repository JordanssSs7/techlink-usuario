package com.techlink.service;

import com.techlink.dto.ReporteRequest;
import com.techlink.dto.ReporteResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Reporte;
import com.techlink.model.Sesion;
import com.techlink.repository.ReporteRepository;
import com.techlink.repository.SesionRepository;
import com.techlink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.techlink.service.PruebaSesiones.USUARIO_ESTUDIANTE;
import static com.techlink.service.PruebaSesiones.USUARIO_MENTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-29: reportar a un usuario o una sesión.
class ReporteServiceTest {

    private static final String TEXTO = "El mentor no se conectó y no avisó a tiempo.";

    private ReporteRepository reporteRepository;
    private SesionRepository sesionRepository;
    private UsuarioRepository usuarioRepository;
    private ReporteService servicio;

    @BeforeEach
    void preparar() {
        reporteRepository = Mockito.mock(ReporteRepository.class);
        sesionRepository = Mockito.mock(SesionRepository.class);
        usuarioRepository = Mockito.mock(UsuarioRepository.class);
        servicio = new ReporteService(reporteRepository, sesionRepository, usuarioRepository);
        when(reporteRepository.existsByIdReportanteAndIdUsuarioReportadoAndIdSesionAndEstado(any(), any(), any(), any()))
                .thenReturn(false);
        when(reporteRepository.save(any(Reporte.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Sesion sesion() {
        Sesion s = PruebaSesiones.sesion(1, LocalDateTime.now().minusHours(3), 30, Sesion.EstadoSesion.CONFIRMADA);
        when(sesionRepository.findById(1)).thenReturn(Optional.of(s));
        return s;
    }

    private static ReporteRequest deSesion(String motivo, String descripcion) {
        return new ReporteRequest("SESION", 1, null, motivo, descripcion);
    }

    private static ReglaNegocioException error(Runnable accion) {
        return assertThrows(ReglaNegocioException.class, accion::run);
    }

    @Test
    void elEstudianteReportaUnaSesionYQuedaReportadoElMentor() {
        sesion();

        ReporteResponse r = servicio.reportar(USUARIO_ESTUDIANTE, deSesion("INCUMPLIMIENTO", TEXTO));

        ArgumentCaptor<Reporte> guardado = ArgumentCaptor.forClass(Reporte.class);
        verify(reporteRepository).save(guardado.capture());
        assertEquals(USUARIO_ESTUDIANTE, guardado.getValue().getIdReportante());
        assertEquals(USUARIO_MENTOR, guardado.getValue().getIdUsuarioReportado());
        assertEquals(1, guardado.getValue().getIdSesion());
        assertEquals(Reporte.Motivo.INCUMPLIMIENTO, guardado.getValue().getMotivo());
        assertEquals("PENDIENTE", r.estado());
    }

    @Test
    void elMentorReportaUnaSesionYQuedaReportadoElEstudiante() {
        sesion();

        servicio.reportar(USUARIO_MENTOR, deSesion("CONDUCTA_INADECUADA", TEXTO));

        ArgumentCaptor<Reporte> guardado = ArgumentCaptor.forClass(Reporte.class);
        verify(reporteRepository).save(guardado.capture());
        assertEquals(USUARIO_ESTUDIANTE, guardado.getValue().getIdUsuarioReportado());
    }

    @Test
    void unaPersonaAjenaALaSesionNoPuedeReportarla() {
        sesion();
        assertEquals(HttpStatus.NOT_FOUND, error(() -> servicio.reportar(99, deSesion("SPAM", TEXTO))).getEstado());
        verify(reporteRepository, never()).save(any());
    }

    @Test
    void reportaAUnUsuarioSinSesion() {
        when(usuarioRepository.existsById(20)).thenReturn(true);

        servicio.reportar(USUARIO_ESTUDIANTE, new ReporteRequest("USUARIO", null, 20, "ACOSO", TEXTO));

        ArgumentCaptor<Reporte> guardado = ArgumentCaptor.forClass(Reporte.class);
        verify(reporteRepository).save(guardado.capture());
        assertEquals(20, guardado.getValue().getIdUsuarioReportado());
        assertNull(guardado.getValue().getIdSesion());
    }

    @Test
    void noPuedeReportarseASiMismo() {
        ReglaNegocioException e = error(() ->
                servicio.reportar(10, new ReporteRequest("USUARIO", null, 10, "OTRO", TEXTO)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getEstado());
    }

    @Test
    void rechazaUnUsuarioQueNoExiste() {
        when(usuarioRepository.existsById(77)).thenReturn(false);
        ReglaNegocioException e = error(() ->
                servicio.reportar(10, new ReporteRequest("USUARIO", null, 77, "OTRO", TEXTO)));
        assertEquals(HttpStatus.NOT_FOUND, e.getEstado());
    }

    @Test
    void exigeUnaDescripcionConAlMenosDiezCaracteres() {
        sesion();
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.reportar(10, deSesion("OTRO", "  corto  "))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.reportar(10, deSesion("OTRO", null))).getEstado());
    }

    @Test
    void rechazaUnaDescripcionDeMasDeQuinientosCaracteres() {
        sesion();
        assertEquals(HttpStatus.BAD_REQUEST,
                error(() -> servicio.reportar(10, deSesion("OTRO", "x".repeat(501)))).getEstado());
    }

    @Test
    void rechazaTipoOMotivoInvalidos() {
        sesion();
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.reportar(10, deSesion("INVENTADO", TEXTO))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST,
                error(() -> servicio.reportar(10, new ReporteRequest("OTRA", 1, null, "SPAM", TEXTO))).getEstado());
    }

    @Test
    void noRepiteUnReporteQueTodaviaSeEstaRevisando() {
        sesion();
        when(reporteRepository.existsByIdReportanteAndIdUsuarioReportadoAndIdSesionAndEstado(
                USUARIO_ESTUDIANTE, USUARIO_MENTOR, 1, Reporte.Estado.PENDIENTE)).thenReturn(true);

        assertEquals(HttpStatus.CONFLICT, error(() -> servicio.reportar(USUARIO_ESTUDIANTE, deSesion("SPAM", TEXTO))).getEstado());
        verify(reporteRepository, never()).save(any());
    }
}
