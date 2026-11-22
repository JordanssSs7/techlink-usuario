package com.techlink.service;

import com.techlink.dto.NotificacionDtos.NotificacionResponse;
import com.techlink.dto.NotificacionDtos.NotificacionesResponse;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Notificacion;
import com.techlink.model.Notificacion.Tipo;
import com.techlink.repository.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-22: centro de notificaciones (crear, listar, marcar como leídas).
class NotificacionServiceTest {

    private static final int USUARIO = 7;

    private NotificacionRepository repositorio;
    private PreferenciaNotificacionService preferencias;
    private NotificacionService servicio;

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(NotificacionRepository.class);
        preferencias = Mockito.mock(PreferenciaNotificacionService.class);
        servicio = new NotificacionService(repositorio, preferencias);
        when(preferencias.permite(anyInt(), any(), any())).thenReturn(true);
    }

    private static Notificacion notificacion(int id, int usuario, boolean leida) {
        Notificacion n = new Notificacion(usuario, Tipo.SESION, "Sesión confirmada", "Tu sesión quedó confirmada.");
        n.setId(id);
        n.setLeida(leida);
        return n;
    }

    @Test
    void crearGuardaElAvisoParaElUsuario() {
        assertTrue(servicio.crear(USUARIO, Tipo.SESION, "Sesión confirmada", "Tu sesión quedó confirmada."));

        ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
        verify(repositorio).save(guardada.capture());
        assertEquals(USUARIO, guardada.getValue().getIdUsuario());
        assertEquals(Tipo.SESION, guardada.getValue().getTipo());
        assertFalse(guardada.getValue().isLeida());
    }

    @Test
    void crearNoGuardaNadaSiElUsuarioDesactivoEseTipoEnLaApp() {
        when(preferencias.permite(USUARIO, Tipo.ANUNCIO, PreferenciaNotificacionService.Canal.EN_APP)).thenReturn(false);

        assertFalse(servicio.crear(USUARIO, Tipo.ANUNCIO, "Novedad", "Hay una charla nueva."));

        verify(repositorio, never()).save(any());
    }

    @Test
    void crearRecortaLosTextosMuyLargos() {
        servicio.crear(USUARIO, Tipo.ANUNCIO, "t".repeat(400), "m".repeat(900));

        ArgumentCaptor<Notificacion> guardada = ArgumentCaptor.forClass(Notificacion.class);
        verify(repositorio).save(guardada.capture());
        assertEquals(150, guardada.getValue().getTitulo().length());
        assertEquals(500, guardada.getValue().getMensaje().length());
    }

    @Test
    void listarDevuelveLasDelUsuarioYElTotalDeNoLeidas() {
        when(repositorio.findByIdUsuarioOrderByFechaCreacionDescIdDesc(eq(USUARIO), any(Pageable.class)))
                .thenReturn(List.of(notificacion(2, USUARIO, false), notificacion(1, USUARIO, true)));
        when(repositorio.countByIdUsuarioAndLeidaFalse(USUARIO)).thenReturn(5L);

        NotificacionesResponse respuesta = servicio.listar(USUARIO, false, null);

        assertEquals(5, respuesta.noLeidas());
        assertEquals(2, respuesta.notificaciones().size());
        assertEquals("SESION", respuesta.notificaciones().get(0).tipo());
        assertFalse(respuesta.notificaciones().get(0).leida());
    }

    @Test
    void listarSoloNoLeidasUsaElFiltroDePendientes() {
        when(repositorio.findByIdUsuarioAndLeidaFalseOrderByFechaCreacionDescIdDesc(eq(USUARIO), any(Pageable.class)))
                .thenReturn(List.of(notificacion(2, USUARIO, false)));

        NotificacionesResponse respuesta = servicio.listar(USUARIO, true, 10);

        assertEquals(1, respuesta.notificaciones().size());
        verify(repositorio, never()).findByIdUsuarioOrderByFechaCreacionDescIdDesc(anyInt(), any());
    }

    @Test
    void elLimiteSeAjustaEntre1Y100() {
        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        when(repositorio.findByIdUsuarioOrderByFechaCreacionDescIdDesc(eq(USUARIO), any())).thenReturn(List.of());

        servicio.listar(USUARIO, false, 5000);
        servicio.listar(USUARIO, false, -3);
        servicio.listar(USUARIO, false, null);

        verify(repositorio, Mockito.times(3)).findByIdUsuarioOrderByFechaCreacionDescIdDesc(eq(USUARIO), pagina.capture());
        assertEquals(100, pagina.getAllValues().get(0).getPageSize());
        assertEquals(1, pagina.getAllValues().get(1).getPageSize());
        assertEquals(30, pagina.getAllValues().get(2).getPageSize());
    }

    @Test
    void marcarLeidaCambiaElEstadoDeLaNotificacion() {
        Notificacion n = notificacion(3, USUARIO, false);
        when(repositorio.findByIdAndIdUsuario(3, USUARIO)).thenReturn(Optional.of(n));
        when(repositorio.save(n)).thenReturn(n);

        NotificacionResponse respuesta = servicio.marcarLeida(USUARIO, 3);

        assertTrue(respuesta.leida());
        assertTrue(n.isLeida());
    }

    @Test
    void noSePuedeMarcarLaNotificacionDeOtroUsuario() {
        when(repositorio.findByIdAndIdUsuario(3, USUARIO)).thenReturn(Optional.empty());

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class, () -> servicio.marcarLeida(USUARIO, 3));

        assertEquals(HttpStatus.NOT_FOUND, error.getEstado());
        verify(repositorio, never()).save(any());
    }

    @Test
    void marcarTodasLeidasDevuelveCuantasCambiaron() {
        when(repositorio.marcarTodasLeidas(USUARIO)).thenReturn(4);

        assertEquals(4, servicio.marcarTodasLeidas(USUARIO));
    }
}
