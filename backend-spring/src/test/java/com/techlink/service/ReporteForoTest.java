package com.techlink.service;

import com.techlink.dto.ReporteRequest;
import com.techlink.dto.ReporteResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Consulta;
import com.techlink.model.Reporte;
import com.techlink.model.Respuesta;
import com.techlink.repository.ConsultaRepository;
import com.techlink.repository.ReporteRepository;
import com.techlink.repository.RespuestaRepository;
import com.techlink.repository.SesionRepository;
import com.techlink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-29 aplicada al foro: cualquier usuario puede reportar una consulta o una respuesta de otra persona.
class ReporteForoTest {

    private static final int AUTOR = 10;
    private static final int LECTOR = 20;
    private static final String TEXTO = "Este mensaje insulta a otros estudiantes del curso.";

    private ReporteRepository reporteRepository;
    private ConsultaRepository consultaRepository;
    private RespuestaRepository respuestaRepository;
    private ReporteService servicio;
    private Consulta consulta;
    private Respuesta respuesta;

    @BeforeEach
    void preparar() {
        reporteRepository = Mockito.mock(ReporteRepository.class);
        consultaRepository = Mockito.mock(ConsultaRepository.class);
        respuestaRepository = Mockito.mock(RespuestaRepository.class);
        servicio = new ReporteService(reporteRepository, Mockito.mock(SesionRepository.class),
                Mockito.mock(UsuarioRepository.class), consultaRepository, respuestaRepository);
        when(reporteRepository.save(any(Reporte.class))).thenAnswer(i -> i.getArgument(0));

        consulta = new Consulta();
        consulta.setId(5);
        consulta.setAutor(PruebaSesiones.usuario(AUTOR, "Ana", "Quispe", "ana@tecsup.edu.pe"));
        consulta.setTitulo("¿Cómo funciona un JOIN?");
        consulta.setContenido("No entiendo la diferencia entre INNER y LEFT JOIN.");
        when(consultaRepository.findByIdAndOcultoFalse(5)).thenReturn(Optional.of(consulta));

        respuesta = new Respuesta();
        respuesta.setId(7);
        respuesta.setAutor(PruebaSesiones.usuario(AUTOR, "Ana", "Quispe", "ana@tecsup.edu.pe"));
        respuesta.setContenido("Una respuesta de prueba");
        when(respuestaRepository.findById(7)).thenReturn(Optional.of(respuesta));
    }

    private static ReporteRequest deConsulta(int id) {
        return new ReporteRequest("CONSULTA", null, null, "CONTENIDO_OFENSIVO", TEXTO, id, null);
    }

    private static ReporteRequest deRespuesta(int id) {
        return new ReporteRequest("RESPUESTA", null, null, "ACOSO", TEXTO, null, id);
    }

    @Test
    void cualquierUsuarioPuedeReportarUnaConsultaDelForo() {
        ReporteResponse r = servicio.reportar(LECTOR, deConsulta(5));

        ArgumentCaptor<Reporte> guardado = ArgumentCaptor.forClass(Reporte.class);
        verify(reporteRepository).save(guardado.capture());
        Reporte reporte = guardado.getValue();
        assertEquals(Reporte.Tipo.CONSULTA, reporte.getTipo());
        assertEquals(5, reporte.getIdConsulta());
        assertEquals(AUTOR, reporte.getIdUsuarioReportado(), "se reporta a quien la escribió");
        assertEquals(LECTOR, reporte.getIdReportante());
        assertNull(reporte.getIdSesion());
        assertEquals("CONSULTA", r.tipo());
    }

    @Test
    void elReporteGuardaLoQueDeciaLaConsulta() {
        servicio.reportar(LECTOR, deConsulta(5));

        ArgumentCaptor<Reporte> guardado = ArgumentCaptor.forClass(Reporte.class);
        verify(reporteRepository).save(guardado.capture());
        assertTrue(guardado.getValue().getContenidoReportado().startsWith("¿Cómo funciona un JOIN?: No entiendo"));
    }

    @Test
    void siSeReportaUnTextoMuyLargoSeGuardaRecortado() {
        consulta.setContenido("palabra ".repeat(200));

        servicio.reportar(LECTOR, deConsulta(5));

        ArgumentCaptor<Reporte> guardado = ArgumentCaptor.forClass(Reporte.class);
        verify(reporteRepository).save(guardado.capture());
        assertEquals(500, guardado.getValue().getContenidoReportado().length());
    }

    @Test
    void cualquierUsuarioPuedeReportarUnaRespuestaDelForo() {
        servicio.reportar(LECTOR, deRespuesta(7));

        ArgumentCaptor<Reporte> guardado = ArgumentCaptor.forClass(Reporte.class);
        verify(reporteRepository).save(guardado.capture());
        assertEquals(Reporte.Tipo.RESPUESTA, guardado.getValue().getTipo());
        assertEquals(7, guardado.getValue().getIdRespuesta());
        assertEquals("Una respuesta de prueba", guardado.getValue().getContenidoReportado());
    }

    @Test
    void noSePuedeReportarLoPropio() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ReglaNegocioException.class, () -> servicio.reportar(AUTOR, deConsulta(5))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ReglaNegocioException.class, () -> servicio.reportar(AUTOR, deRespuesta(7))).getEstado());
        verify(reporteRepository, never()).save(any());
    }

    @Test
    void reportarAlgoQueNoExisteEsNoEncontrado() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ReglaNegocioException.class, () -> servicio.reportar(LECTOR, deConsulta(99))).getEstado());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ReglaNegocioException.class, () -> servicio.reportar(LECTOR, deRespuesta(99))).getEstado());
    }

    @Test
    void unaRespuestaOcultaPorElAdministradorNoSePuedeReportar() {
        org.springframework.test.util.ReflectionTestUtils.setField(respuesta, "oculto", true);

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ReglaNegocioException.class, () -> servicio.reportar(LECTOR, deRespuesta(7))).getEstado());
    }

    @Test
    void sinIndicarQueSeReportaEsUnaPeticionInvalida() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ReglaNegocioException.class,
                () -> servicio.reportar(LECTOR, new ReporteRequest("CONSULTA", null, null, "SPAM", TEXTO))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ReglaNegocioException.class,
                () -> servicio.reportar(LECTOR, new ReporteRequest("RESPUESTA", null, null, "SPAM", TEXTO))).getEstado());
    }

    @Test
    void noSeRepiteUnReportePendienteSobreLoMismo() {
        when(reporteRepository.existsByIdReportanteAndIdConsultaAndEstado(LECTOR, 5, Reporte.Estado.PENDIENTE)).thenReturn(true);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> servicio.reportar(LECTOR, deConsulta(5)));

        assertEquals(HttpStatus.CONFLICT, error.getEstado());
        verify(reporteRepository, never()).save(any());
    }

    @Test
    void reportarUnaConsultaNoChocaConUnReporteDeUsuarioPendienteDeLaMismaPersona() {
        // Un reporte pendiente contra la misma persona (tipo USUARIO) no impide reportar una consulta suya.
        when(reporteRepository.existsByIdReportanteAndIdUsuarioReportadoAndIdSesionAndEstado(any(), any(), any(), any())).thenReturn(true);

        servicio.reportar(LECTOR, deConsulta(5));

        verify(reporteRepository).save(any(Reporte.class));
    }

    @Test
    void elDescripcionDelReporteSeSigueExigiendo() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ReglaNegocioException.class,
                () -> servicio.reportar(LECTOR, new ReporteRequest("CONSULTA", null, null, "SPAM", "corto", 5, null))).getEstado());
    }
}
