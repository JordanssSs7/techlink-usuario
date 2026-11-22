package com.techlink.service;

import com.techlink.model.Notificacion.Tipo;
import com.techlink.model.Sesion;
import com.techlink.model.SesionRecordatorio;
import com.techlink.model.SesionRecordatorio.Momento;
import com.techlink.repository.SesionRecordatorioRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.techlink.service.PruebaSesiones.USUARIO_ESTUDIANTE;
import static com.techlink.service.PruebaSesiones.USUARIO_MENTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-23: recordatorios 24 horas y 1 hora antes de cada sesión.
class RecordatorioSesionServiceTest {

    // "Ahora" para las pruebas: 20/11/2026 a las 12:00.
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 20, 12, 0);

    private SesionRepository sesionRepository;
    private SesionRecordatorioRepository recordatorioRepository;
    private NotificacionService notificaciones;
    private SesionNotificacionService correos;
    private RecordatorioSesionService servicio;

    @BeforeEach
    void preparar() {
        sesionRepository = Mockito.mock(SesionRepository.class);
        recordatorioRepository = Mockito.mock(SesionRecordatorioRepository.class);
        notificaciones = Mockito.mock(NotificacionService.class);
        correos = Mockito.mock(SesionNotificacionService.class);
        Clock reloj = Clock.fixed(AHORA.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        servicio = new RecordatorioSesionService(sesionRepository, recordatorioRepository, notificaciones, correos, reloj);
    }

    private void haySesiones(Sesion... sesiones) {
        when(sesionRepository.confirmadasEntre(any(), any())).thenReturn(List.of(sesiones));
    }

    private static Sesion sesion(int id, LocalDateTime inicio, Sesion.EstadoSesion estado) {
        return PruebaSesiones.sesion(id, inicio, 60, estado);
    }

    @Test
    void elMomentoDependeDeCuantoFalta() {
        assertEquals(Momento.H24, RecordatorioSesionService.momento(AHORA.plusHours(23), AHORA));
        assertEquals(Momento.H24, RecordatorioSesionService.momento(AHORA.plusHours(24), AHORA));
        assertEquals(Momento.H24, RecordatorioSesionService.momento(AHORA.plusMinutes(61), AHORA));
        assertEquals(Momento.H1, RecordatorioSesionService.momento(AHORA.plusMinutes(60), AHORA));
        assertEquals(Momento.H1, RecordatorioSesionService.momento(AHORA.plusMinutes(5), AHORA));
        assertNull(RecordatorioSesionService.momento(AHORA.plusHours(25), AHORA));
        assertNull(RecordatorioSesionService.momento(AHORA, AHORA));
        assertNull(RecordatorioSesionService.momento(AHORA.minusMinutes(10), AHORA));
    }

    @Test
    void conUnaSesionEnMenosDe24HorasSeAvisaAlEstudianteYAlMentor() {
        haySesiones(sesion(1, AHORA.plusHours(20), Sesion.EstadoSesion.CONFIRMADA));

        assertEquals(1, servicio.enviarPendientes());

        ArgumentCaptor<SesionRecordatorio> registro = ArgumentCaptor.forClass(SesionRecordatorio.class);
        verify(recordatorioRepository).save(registro.capture());
        assertEquals(Momento.H24, registro.getValue().getMomento());
        assertEquals(AHORA.plusHours(20), registro.getValue().getInicioSesion());
        verify(notificaciones).crear(eq(USUARIO_ESTUDIANTE), eq(Tipo.RECORDATORIO), anyString(), anyString());
        verify(notificaciones).crear(eq(USUARIO_MENTOR), eq(Tipo.RECORDATORIO), anyString(), anyString());
        verify(correos).enviarRecordatorio(eq("ana@tecsup.edu.pe"), anyString(), eq("menos de 24 horas"), any(), any(), any(), anyString());
        verify(correos).enviarRecordatorio(eq("lucia@tecsup.edu.pe"), anyString(), eq("menos de 24 horas"), any(), any(), any(), anyString());
    }

    @Test
    void conUnaSesionEnMenosDeUnaHoraElRecordatorioEsH1() {
        haySesiones(sesion(1, AHORA.plusMinutes(40), Sesion.EstadoSesion.CONFIRMADA));

        servicio.enviarPendientes();

        ArgumentCaptor<SesionRecordatorio> registro = ArgumentCaptor.forClass(SesionRecordatorio.class);
        verify(recordatorioRepository).save(registro.capture());
        assertEquals(Momento.H1, registro.getValue().getMomento());
        verify(correos, times(2)).enviarRecordatorio(anyString(), anyString(), eq("menos de 1 hora"), any(), any(), any(), anyString());
    }

    @Test
    void elMensajeIncluyeElEnlaceDeLaReunion() {
        haySesiones(sesion(1, AHORA.plusHours(3), Sesion.EstadoSesion.CONFIRMADA));

        servicio.enviarPendientes();

        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(notificaciones).crear(eq(USUARIO_ESTUDIANTE), eq(Tipo.RECORDATORIO), anyString(), mensaje.capture());
        assertEquals(true, mensaje.getValue().contains("https://meet.google.com/prueba"));
        assertEquals(true, mensaje.getValue().contains("Lucía"), "al estudiante se le dice con qué mentor es");
    }

    @Test
    void unRecordatorioYaEnviadoNoSeRepite() {
        Sesion s = sesion(1, AHORA.plusHours(20), Sesion.EstadoSesion.CONFIRMADA);
        haySesiones(s);
        when(recordatorioRepository.existsByIdSesionAndMomentoAndInicioSesion(1, Momento.H24, AHORA.plusHours(20))).thenReturn(true);

        assertEquals(0, servicio.enviarPendientes());

        verify(recordatorioRepository, never()).save(any());
        verify(correos, never()).enviarRecordatorio(anyString(), anyString(), anyString(), any(), any(), any(), anyString());
        verify(notificaciones, never()).crear(anyInt(), any(), anyString(), anyString());
    }

    @Test
    void unaSesionCanceladaNoGeneraRecordatorios() {
        haySesiones(sesion(1, AHORA.plusHours(20), Sesion.EstadoSesion.CANCELADA));

        assertEquals(0, servicio.enviarPendientes());

        verify(recordatorioRepository, never()).save(any());
        verify(correos, never()).enviarRecordatorio(anyString(), anyString(), anyString(), any(), any(), any(), anyString());
    }

    @Test
    void lasSesionesLejanasOYaIniciadasSeIgnoran() {
        haySesiones(
                sesion(1, AHORA.plusHours(30), Sesion.EstadoSesion.CONFIRMADA),
                sesion(2, AHORA.minusHours(1), Sesion.EstadoSesion.CONFIRMADA));

        assertEquals(0, servicio.enviarPendientes());

        verify(recordatorioRepository, never()).save(any());
    }

    @Test
    void siFallaElCorreoElRecordatorioIgualQuedaRegistradoYSeSigueConLosDemas() {
        haySesiones(
                sesion(1, AHORA.plusHours(5), Sesion.EstadoSesion.CONFIRMADA),
                sesion(2, AHORA.plusHours(6), Sesion.EstadoSesion.CONFIRMADA));
        doThrow(new IllegalStateException("SMTP caído")).when(correos)
                .enviarRecordatorio(anyString(), anyString(), anyString(), any(), any(), any(), anyString());

        assertEquals(2, servicio.enviarPendientes());

        verify(recordatorioRepository, times(2)).save(any());
        verify(notificaciones, times(4)).crear(anyInt(), eq(Tipo.RECORDATORIO), anyString(), anyString());
    }

    @Test
    void unaSesionReprogramadaVuelveAAvisarPorqueCambioSuInicio() {
        Sesion s = sesion(1, AHORA.plusHours(20), Sesion.EstadoSesion.CONFIRMADA);
        haySesiones(s);
        // Del horario anterior ya se avisó, pero este inicio nuevo no.
        when(recordatorioRepository.existsByIdSesionAndMomentoAndInicioSesion(1, Momento.H24, AHORA.plusHours(10))).thenReturn(true);

        assertEquals(1, servicio.enviarPendientes());
    }
}
