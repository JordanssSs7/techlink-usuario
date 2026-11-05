package com.techlink.service;

import com.techlink.dto.CursoResponse;
import com.techlink.exception.CursosCriticosInvalidosException;
import com.techlink.exception.UsuarioNoRegistradoException;
import com.techlink.model.Curso;
import com.techlink.model.Estudiante;
import com.techlink.repository.CursoRepository;
import com.techlink.repository.EstudianteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas unitarias de los cursos criticos del estudiante (HU-04): no necesitan base de datos.
class CursoCriticoServiceTest {

    private static final Integer ID_USUARIO = 7;

    private EstudianteRepository estudianteRepository;
    private CursoRepository cursoRepository;
    private CursoCriticoService service;
    private Estudiante estudiante;

    @BeforeEach
    void preparar() {
        estudianteRepository = Mockito.mock(EstudianteRepository.class);
        cursoRepository = Mockito.mock(CursoRepository.class);
        service = new CursoCriticoService(estudianteRepository, cursoRepository);

        estudiante = new Estudiante();
        when(estudianteRepository.findByUsuarioId(ID_USUARIO)).thenReturn(Optional.of(estudiante));
        when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(i -> i.getArgument(0));

        agregarCurso(1, "Base de Datos", true);
        agregarCurso(2, "Estructuras de Datos y Algoritmos", true);
        agregarCurso(3, "Fundamentos de Programación", true);
        agregarCurso(4, "Programación Orientada a Objetos", true);
        agregarCurso(5, "Programación en Móviles", true);
        agregarCurso(6, "Tecnologías Emergentes", true);
        agregarCurso(99, "Curso dado de baja", false);
    }

    private void agregarCurso(Integer id, String nombre, boolean activo) {
        Curso curso = new Curso();
        curso.setId(id);
        curso.setNombre(nombre);
        curso.setActivo(activo);
        when(cursoRepository.findById(id)).thenReturn(Optional.of(curso));
    }

    @Test
    void guardaLosCursosElegidosYLosDevuelveEnOrdenAlfabetico() {
        List<CursoResponse> respuesta = service.guardar(ID_USUARIO, List.of(2, 1));

        assertEquals(2, estudiante.getCursosCriticos().size());
        assertEquals("Base de Datos", respuesta.get(0).nombre());
        assertEquals("Estructuras de Datos y Algoritmos", respuesta.get(1).nombre());
    }

    @Test
    void laNuevaListaReemplazaALaAnterior() {
        service.guardar(ID_USUARIO, List.of(1, 2));

        List<CursoResponse> respuesta = service.guardar(ID_USUARIO, List.of(3));

        assertEquals(1, respuesta.size());
        assertEquals(3, respuesta.get(0).id());
        assertEquals(1, estudiante.getCursosCriticos().size());
    }

    @Test
    void unaListaVaciaQuitaTodosLosCursos() {
        service.guardar(ID_USUARIO, List.of(1, 2));

        List<CursoResponse> respuesta = service.guardar(ID_USUARIO, List.of());

        assertTrue(respuesta.isEmpty());
        assertTrue(estudiante.getCursosCriticos().isEmpty());
    }

    @Test
    void losCursosRepetidosSeGuardanUnaSolaVez() {
        List<CursoResponse> respuesta = service.guardar(ID_USUARIO, List.of(1, 1, 2, 2));

        assertEquals(2, respuesta.size());
    }

    @Test
    void losValoresNulosSeIgnoran() {
        List<CursoResponse> respuesta = service.guardar(ID_USUARIO, Arrays.asList(1, null));

        assertEquals(1, respuesta.size());
    }

    @Test
    void unCursoQueNoExisteEsRechazado() {
        when(cursoRepository.findById(1000)).thenReturn(Optional.empty());

        CursosCriticosInvalidosException error = assertThrows(CursosCriticosInvalidosException.class,
                () -> service.guardar(ID_USUARIO, List.of(1, 1000)));

        assertTrue(error.getCampos().get("cursos").contains("1000"));
        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    void unCursoInactivoNoSePuedeElegir() {
        assertThrows(CursosCriticosInvalidosException.class, () -> service.guardar(ID_USUARIO, List.of(99)));
        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    void seAceptanHastaCincoCursosYSeRechazanSeis() {
        assertEquals(5, service.guardar(ID_USUARIO, List.of(1, 2, 3, 4, 5)).size());

        CursosCriticosInvalidosException error = assertThrows(CursosCriticosInvalidosException.class,
                () -> service.guardar(ID_USUARIO, List.of(1, 2, 3, 4, 5, 6)));

        assertTrue(error.getCampos().get("cursos").contains("5"));
    }

    @Test
    void sinCursosGuardadosLaListaEstaVacia() {
        assertTrue(service.listar(ID_USUARIO).isEmpty());
    }

    @Test
    void listarDevuelveLosCursosGuardados() {
        service.guardar(ID_USUARIO, List.of(3, 1));

        List<CursoResponse> respuesta = service.listar(ID_USUARIO);

        assertEquals(List.of("Base de Datos", "Fundamentos de Programación"),
                respuesta.stream().map(CursoResponse::nombre).toList());
    }

    @Test
    void quienNoTienePerfilDeEstudianteNoPuedeElegirCursos() {
        when(estudianteRepository.findByUsuarioId(50)).thenReturn(Optional.empty());

        assertThrows(UsuarioNoRegistradoException.class, () -> service.guardar(50, List.of(1)));
        assertThrows(UsuarioNoRegistradoException.class, () -> service.listar(50));
    }
}
