package com.techlink.service;

import com.techlink.dto.CriteriosEmparejamientoResponse;
import com.techlink.dto.PesosEmparejamientoRequest;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.CriterioEmparejamiento;
import com.techlink.repository.CriterioEmparejamientoRepository;
import com.techlink.service.CriteriosEmparejamientoService.Pesos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-61: los pesos del emparejamiento con IA que ajusta el administrador.
class CriteriosEmparejamientoServiceTest {

    private CriterioEmparejamientoRepository repositorio;
    private JdbcTemplate jdbc;
    private CriteriosEmparejamientoService servicio;

    @BeforeEach
    void preparar() {
        repositorio = Mockito.mock(CriterioEmparejamientoRepository.class);
        jdbc = Mockito.mock(JdbcTemplate.class);
        servicio = new CriteriosEmparejamientoService(repositorio, jdbc);
        when(repositorio.findAll()).thenReturn(List.of(
                new CriterioEmparejamiento("CURSOS", 70), new CriterioEmparejamiento("CALIFICACION", 0),
                new CriterioEmparejamiento("DISPONIBILIDAD", 30)));
    }

    private static ReglaNegocioException error(Runnable accion) {
        return assertThrows(ReglaNegocioException.class, accion::run);
    }

    @Test
    void leeLosPesosGuardados() {
        assertEquals(new Pesos(70, 0, 30), servicio.actuales());
    }

    @Test
    void siFaltanLosPesosEnLaBaseUsaLosIniciales() {
        when(repositorio.findAll()).thenReturn(List.of());
        assertEquals(Pesos.PREDETERMINADOS, servicio.actuales());
    }

    @Test
    void unPesoFueraDeRangoEnLaBaseVuelveALosIniciales() {
        when(repositorio.findAll()).thenReturn(List.of(new CriterioEmparejamiento("CURSOS", 500)));
        assertEquals(Pesos.PREDETERMINADOS, servicio.actuales());
    }

    @Test
    void lasFraccionesSonProporcionalesYSumanUno() {
        Pesos p = new Pesos(50, 25, 25);
        assertEquals(0.5, p.fraccionCursos(), 0.0001);
        assertEquals(1.0, p.fraccionCursos() + p.fraccionCalificacion() + p.fraccionDisponibilidad(), 0.0001);
        // 10/10/10 pesa igual que 33/33/33: lo que importa es la proporcion.
        assertEquals(new Pesos(10, 10, 10).fraccionCursos(), new Pesos(33, 33, 33).fraccionCursos(), 0.0001);
    }

    @Test
    void guardaLosTresPesosYAnotaLaAccionDelAdministrador() {
        when(repositorio.findById(anyString())).thenAnswer(i -> Optional.of(new CriterioEmparejamiento(i.getArgument(0), 0)));

        CriteriosEmparejamientoResponse r = servicio.guardar(5, new PesosEmparejamientoRequest(40, 40, 20));

        ArgumentCaptor<CriterioEmparejamiento> guardados = ArgumentCaptor.forClass(CriterioEmparejamiento.class);
        verify(repositorio, times(3)).save(guardados.capture());
        assertEquals(List.of(40, 40, 20), guardados.getAllValues().stream().map(CriterioEmparejamiento::getPeso).toList());
        assertEquals(List.of(5, 5, 5), guardados.getAllValues().stream().map(CriterioEmparejamiento::getIdAdmin).toList());
        verify(jdbc).update(anyString(), eq(5), eq("CRITERIOS_IA_ACTUALIZADOS"), eq("emparejamiento"), anyString());
        assertEquals(70, r.predeterminados().cursos());
    }

    @Test
    void siNoCambiaNingunPesoNoAnotaNada() {
        when(repositorio.findById(anyString())).thenAnswer(i -> Optional.of(new CriterioEmparejamiento(i.getArgument(0), 0)));

        servicio.guardar(5, new PesosEmparejamientoRequest(70, 0, 30));

        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void rechazaPesosInvalidos() {
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.guardar(5, new PesosEmparejamientoRequest(0, 0, 0))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.guardar(5, new PesosEmparejamientoRequest(-1, 50, 50))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.guardar(5, new PesosEmparejamientoRequest(101, 0, 0))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.guardar(5, new PesosEmparejamientoRequest(50, null, 50))).getEstado());
        assertEquals(HttpStatus.BAD_REQUEST, error(() -> servicio.guardar(5, null)).getEstado());
        verify(repositorio, never()).save(any());
    }
}
