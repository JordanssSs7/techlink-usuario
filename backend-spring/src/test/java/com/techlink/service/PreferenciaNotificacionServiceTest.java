package com.techlink.service;

import com.techlink.dto.NotificacionDtos.PreferenciaCambio;
import com.techlink.dto.NotificacionDtos.PreferenciaResponse;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.model.Notificacion.Tipo;
import com.techlink.model.PreferenciaNotificacion;
import com.techlink.model.Usuario;
import com.techlink.repository.PreferenciaNotificacionRepository;
import com.techlink.repository.UsuarioRepository;
import com.techlink.service.PreferenciaNotificacionService.Canal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-24: preferencias de notificación por tipo y canal.
class PreferenciaNotificacionServiceTest {

    private static final int USUARIO = 7;

    private PreferenciaNotificacionRepository repositorio;
    private UsuarioRepository usuarioRepository;
    private PreferenciaNotificacionService servicio;
    private final List<PreferenciaNotificacion> guardadas = new ArrayList<>();

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(PreferenciaNotificacionRepository.class);
        usuarioRepository = Mockito.mock(UsuarioRepository.class);
        servicio = new PreferenciaNotificacionService(repositorio, usuarioRepository);
        when(repositorio.findByIdUsuario(USUARIO)).thenAnswer(i -> List.copyOf(guardadas));
        when(repositorio.findByIdUsuarioAndTipo(anyInt(), any())).thenAnswer(i ->
                guardadas.stream().filter(p -> p.getTipo() == i.getArgument(1)).findFirst());
        when(repositorio.save(any())).thenAnswer(i -> {
            PreferenciaNotificacion p = i.getArgument(0);
            if (!guardadas.contains(p)) {
                guardadas.add(p);
            }
            return p;
        });
    }

    private PreferenciaResponse de(List<PreferenciaResponse> lista, String tipo) {
        return lista.stream().filter(p -> p.tipo().equals(tipo)).findFirst().orElseThrow();
    }

    @Test
    void sinPreferenciasGuardadasTodoEstaActivado() {
        List<PreferenciaResponse> lista = servicio.listar(USUARIO);

        assertEquals(6, lista.size());
        assertTrue(de(lista, "SESION").enApp());
        assertTrue(de(lista, "SESION").correo());
        assertTrue(de(lista, "RECORDATORIO").correo());
    }

    @Test
    void lasCharlasTienenSuPropioTipoSoloEnLaAplicacion() {
        PreferenciaResponse charla = de(servicio.listar(USUARIO), "CHARLA");

        assertEquals("Charlas", charla.nombre());
        assertTrue(charla.enApp());
        assertFalse(charla.correo());
        assertFalse(charla.correoDisponible());
    }

    @Test
    void sePuedenDesactivarLosAvisosDeCharlasSinTocarLosDemasTipos() {
        List<PreferenciaResponse> lista = servicio.guardar(USUARIO, List.of(new PreferenciaCambio("CHARLA", false, null)));

        assertFalse(de(lista, "CHARLA").enApp());
        assertTrue(de(lista, "SESION").enApp());
        assertFalse(servicio.permite(USUARIO, Tipo.CHARLA, PreferenciaNotificacionService.Canal.EN_APP));
        assertTrue(servicio.permite(USUARIO, Tipo.SESION, PreferenciaNotificacionService.Canal.EN_APP));
    }

    @Test
    void losAnunciosNoSeEnvianPorCorreo() {
        PreferenciaResponse anuncio = de(servicio.listar(USUARIO), "ANUNCIO");

        assertTrue(anuncio.enApp());
        assertFalse(anuncio.correo());
        assertFalse(anuncio.correoDisponible());
    }

    @Test
    void guardarCreaLaPreferenciaYDevuelveLaListaActualizada() {
        List<PreferenciaResponse> lista = servicio.guardar(USUARIO, List.of(new PreferenciaCambio("RECORDATORIO", true, false)));

        assertTrue(de(lista, "RECORDATORIO").enApp());
        assertFalse(de(lista, "RECORDATORIO").correo());
        assertTrue(de(lista, "SESION").correo());
    }

    @Test
    void guardarActualizaSoloLoQueSeEnvia() {
        servicio.guardar(USUARIO, List.of(new PreferenciaCambio("SESION", false, null)));
        List<PreferenciaResponse> lista = servicio.guardar(USUARIO, List.of(new PreferenciaCambio("SESION", null, false)));

        assertFalse(de(lista, "SESION").enApp());
        assertFalse(de(lista, "SESION").correo());
        assertEquals(1, guardadas.size());
    }

    @Test
    void elTipoSeAceptaEnMinusculas() {
        List<PreferenciaResponse> lista = servicio.guardar(USUARIO, List.of(new PreferenciaCambio("sesion", false, null)));

        assertFalse(de(lista, "SESION").enApp());
    }

    @Test
    void unTipoInexistenteSeRechazaYNoGuardaNada() {
        DatosInvalidosException error = assertThrows(DatosInvalidosException.class, () ->
                servicio.guardar(USUARIO, List.of(new PreferenciaCambio("OFERTAS", true, true))));

        assertTrue(error.getCampos().containsKey("tipo"));
        verify(repositorio, never()).save(any());
    }

    @Test
    void sinCambiosSeRechaza() {
        assertThrows(DatosInvalidosException.class, () -> servicio.guardar(USUARIO, List.of()));
        assertThrows(DatosInvalidosException.class, () -> servicio.guardar(USUARIO, null));
    }

    @Test
    void activarElCorreoDeLosAnunciosNoTieneEfecto() {
        ArgumentCaptor<PreferenciaNotificacion> guardada = ArgumentCaptor.forClass(PreferenciaNotificacion.class);

        servicio.guardar(USUARIO, List.of(new PreferenciaCambio("ANUNCIO", false, true)));

        verify(repositorio).save(guardada.capture());
        assertFalse(guardada.getValue().isEnApp());
        assertTrue(guardada.getValue().isCorreo(), "el valor guardado no cambia: se mantiene el predeterminado");
        assertFalse(servicio.permite(USUARIO, Tipo.ANUNCIO, Canal.CORREO), "pero nunca se envía por correo");
    }

    @Test
    void permiteSegunLoQueElUsuarioEligio() {
        guardadas.add(new PreferenciaNotificacion(USUARIO, Tipo.RECORDATORIO, true, false));
        guardadas.add(new PreferenciaNotificacion(USUARIO, Tipo.SESION, false, true));

        assertTrue(servicio.permite(USUARIO, Tipo.RECORDATORIO, Canal.EN_APP));
        assertFalse(servicio.permite(USUARIO, Tipo.RECORDATORIO, Canal.CORREO));
        assertFalse(servicio.permite(USUARIO, Tipo.SESION, Canal.EN_APP));
        assertTrue(servicio.permite(USUARIO, Tipo.SESION, Canal.CORREO));
    }

    @Test
    void sinPreferenciaSePermiteTodo() {
        assertTrue(servicio.permite(USUARIO, Tipo.SESION, Canal.EN_APP));
        assertTrue(servicio.permite(USUARIO, Tipo.SESION, Canal.CORREO));
    }

    @Test
    void permiteCorreoAUsaLasPreferenciasDelUsuarioDeEseCorreo() {
        Usuario u = PruebaSesiones.usuario(USUARIO, "Ana", "Quispe", "ana@tecsup.edu.pe");
        when(usuarioRepository.findByCorreoInstitucional("ana@tecsup.edu.pe")).thenReturn(Optional.of(u));
        guardadas.add(new PreferenciaNotificacion(USUARIO, Tipo.SESION, true, false));

        assertFalse(servicio.permiteCorreoA("ana@tecsup.edu.pe", Tipo.SESION));
        assertTrue(servicio.permiteCorreoA("ana@tecsup.edu.pe", Tipo.RECORDATORIO));
    }

    @Test
    void unCorreoDesconocidoSeDejaPasar() {
        when(usuarioRepository.findByCorreoInstitucional("otro@tecsup.edu.pe")).thenReturn(Optional.empty());

        assertTrue(servicio.permiteCorreoA("otro@tecsup.edu.pe", Tipo.SESION));
    }
}
