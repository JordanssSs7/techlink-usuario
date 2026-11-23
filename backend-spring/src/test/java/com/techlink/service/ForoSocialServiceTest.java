package com.techlink.service;

import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Consulta;
import com.techlink.model.ForoGuardado;
import com.techlink.model.ForoReaccion;
import com.techlink.repository.ConsultaRepository;
import com.techlink.repository.ForoGuardadoRepository;
import com.techlink.repository.ForoReaccionRepository;
import com.techlink.repository.RespuestaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Foro social: reacciones (una por persona, repetir la quita, otra la cambia) y consultas guardadas.
class ForoSocialServiceTest {

    private ForoReaccionRepository reacciones;
    private ForoGuardadoRepository guardados;
    private ConsultaRepository consultas;
    private ForoSocialService servicio;
    private NotificacionService notificaciones;
    private final List<ForoReaccion> guardadasEnMemoria = new ArrayList<>();

    @BeforeEach
    void preparar() {
        reacciones = Mockito.mock(ForoReaccionRepository.class);
        guardados = Mockito.mock(ForoGuardadoRepository.class);
        consultas = Mockito.mock(ConsultaRepository.class);
        notificaciones = Mockito.mock(NotificacionService.class);
        servicio = new ForoSocialService(reacciones, guardados, consultas, Mockito.mock(RespuestaRepository.class),
                Mockito.mock(ForoService.class), notificaciones, Mockito.mock(com.techlink.repository.UsuarioRepository.class));

        Consulta consulta = new Consulta();
        consulta.setId(10);
        com.techlink.model.Usuario autor = PruebaSesiones.usuario(5, "Ana", "Quispe", "ana@tecsup.edu.pe");
        consulta.setAutor(autor);
        consulta.setTitulo("¿Cómo funciona un JOIN?");
        when(consultas.findByIdAndOcultoFalse(10)).thenReturn(Optional.of(consulta));

        when(reacciones.findByIdUsuarioAndObjetoAndIdObjeto(any(), any(), any())).thenAnswer(i -> guardadasEnMemoria.stream()
                .filter(r -> r.getIdUsuario().equals(i.getArgument(0)) && r.getObjeto().equals(i.getArgument(1))
                        && r.getIdObjeto().equals(i.getArgument(2))).findFirst());
        when(reacciones.findByObjetoAndIdObjetoIn(any(), any())).thenAnswer(i -> guardadasEnMemoria.stream()
                .filter(r -> r.getObjeto().equals(i.getArgument(0))).toList());
        when(reacciones.saveAndFlush(any(ForoReaccion.class))).thenAnswer(i -> {
            ForoReaccion r = i.getArgument(0);
            if (!guardadasEnMemoria.contains(r)) {
                guardadasEnMemoria.add(r);
            }
            return r;
        });
        Mockito.doAnswer(i -> guardadasEnMemoria.remove((ForoReaccion) i.getArgument(0))).when(reacciones).delete(any(ForoReaccion.class));
    }

    @Test
    void reaccionarPoneLaReaccionYContarla() {
        ForoSocialService.Reacciones r = servicio.reaccionarConsulta(1, 10, "ME_GUSTA");

        assertEquals("ME_GUSTA", r.mia());
        assertEquals(1, r.conteos().get("ME_GUSTA"));
    }

    @Test
    void repetirLaMismaReaccionLaQuita() {
        servicio.reaccionarConsulta(1, 10, "ME_GUSTA");

        ForoSocialService.Reacciones r = servicio.reaccionarConsulta(1, 10, "ME_GUSTA");

        assertNull(r.mia());
        assertEquals(0, r.conteos().size());
    }

    @Test
    void otraReaccionReemplazaALaAnterior() {
        servicio.reaccionarConsulta(1, 10, "ME_GUSTA");

        ForoSocialService.Reacciones r = servicio.reaccionarConsulta(1, 10, "ME_DIVIERTE");

        assertEquals("ME_DIVIERTE", r.mia());
        assertEquals(1, r.conteos().size());
    }

    @Test
    void unaReaccionInventadaSeRechaza() {
        assertThrows(ReglaNegocioException.class, () -> servicio.reaccionarConsulta(1, 10, "ODIO"));
        assertThrows(ReglaNegocioException.class, () -> servicio.reaccionarConsulta(1, 10, null));
    }

    @Test
    void noSePuedeReaccionarUnaConsultaInexistente() {
        assertThrows(ReglaNegocioException.class, () -> servicio.reaccionarConsulta(1, 99, "ME_GUSTA"));
    }

    @Test
    void guardarDosVecesNoDuplica() {
        servicio.guardar(1, 10);
        when(guardados.findByIdUsuarioAndIdConsulta(1, 10)).thenReturn(Optional.of(new ForoGuardado(1, 10)));
        servicio.guardar(1, 10);

        verify(guardados, Mockito.times(1)).save(any(ForoGuardado.class));
    }

    @Test
    void guardarOReaccionarAvisaAlAutorPeroNoSiEsElMismo() {
        servicio.guardar(1, 10);
        servicio.reaccionarConsulta(1, 10, "ME_GUSTA");
        servicio.guardar(5, 10);   // el propio autor: no se avisa

        verify(notificaciones, Mockito.times(2)).crear(eq(5), eq(com.techlink.model.Notificacion.Tipo.FORO), any(), any(), any());
    }

    @Test
    void quitarLaReaccionNoAvisa() {
        servicio.reaccionarConsulta(1, 10, "ME_GUSTA");
        servicio.reaccionarConsulta(1, 10, "ME_GUSTA");

        verify(notificaciones, Mockito.times(1)).crear(any(), any(), any(), any(), any());
    }

    @Test
    void sinGuardadosLaListaVaVacia() {
        when(guardados.findByIdUsuarioOrderByIdDesc(1)).thenReturn(List.of());

        assertEquals(0, servicio.guardadas(1).size());
        verify(consultas, never()).findAllById(any());
    }
}
