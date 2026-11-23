package com.techlink.service;

import com.techlink.exception.SesionException;
import com.techlink.model.Estudiante;
import com.techlink.model.Mentor;
import com.techlink.model.Sesion;
import com.techlink.model.SesionGrabacion;
import com.techlink.repository.SesionGrabacionRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

// Grabación de sesiones: el mentor manda el video por partes y queda en un solo archivo.
class GrabacionServiceTest {

    private static final int MENTOR = 10;
    private static final int ESTUDIANTE = 20;
    private static final int SESION = 5;

    @TempDir
    Path carpeta;

    private final Map<Integer, SesionGrabacion> guardadas = new HashMap<>();
    private GrabacionService servicio;

    @BeforeEach
    void preparar() {
        SesionGrabacionRepository repo = Mockito.mock(SesionGrabacionRepository.class);
        SesionRepository sesiones = Mockito.mock(SesionRepository.class);
        Mentor mentor = new Mentor();
        mentor.setUsuario(PruebaSesiones.usuario(MENTOR, "Jordan", "Reyes", "j@tecsup.edu.pe"));
        Estudiante estudiante = new Estudiante();
        estudiante.setUsuario(PruebaSesiones.usuario(ESTUDIANTE, "Ana", "Quispe", "a@tecsup.edu.pe"));
        Sesion sesion = new Sesion();
        sesion.setId(SESION);
        sesion.setMentor(mentor);
        sesion.setEstudiante(estudiante);
        sesion.setEstado(Sesion.EstadoSesion.CONFIRMADA);
        when(sesiones.findById(SESION)).thenReturn(Optional.of(sesion));
        when(repo.findById(anyInt())).thenAnswer(i -> Optional.ofNullable(guardadas.get(i.<Integer>getArgument(0))));
        when(repo.save(any(SesionGrabacion.class))).thenAnswer(i -> {
            SesionGrabacion g = i.getArgument(0);
            guardadas.put(g.getIdSesion(), g);
            return g;
        });
        when(repo.findByIdSesionInAndEstado(any(), any())).thenAnswer(i -> guardadas.values().stream()
                .filter(g -> g.getEstado().equals(i.getArgument(1))).toList());
        servicio = new GrabacionService(repo, sesiones, carpeta.toString(), 1);
    }

    @Test
    void lasPartesSeJuntanEnUnSoloArchivo() throws Exception {
        servicio.agregarParte(MENTOR, SESION, 0, new byte[]{1, 2});
        servicio.agregarParte(MENTOR, SESION, 1, new byte[]{3});
        servicio.terminar(MENTOR, SESION, 90);

        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(servicio.archivoParaVer(ESTUDIANTE, SESION)));
        assertEquals(Set.of(SESION), servicio.conGrabacion(List.of(SESION)));
    }

    @Test
    void unaParteRepetidaNoSeGuardaDosVeces() throws Exception {
        servicio.agregarParte(MENTOR, SESION, 0, new byte[]{1, 2});
        servicio.agregarParte(MENTOR, SESION, 0, new byte[]{1, 2});
        servicio.terminar(MENTOR, SESION, 5);

        assertEquals(2, Files.size(servicio.archivoLista(SESION)));
    }

    @Test
    void soloElMentorDeLaSesionGraba() {
        assertThrows(SesionException.class, () -> servicio.agregarParte(ESTUDIANTE, SESION, 0, new byte[]{1}));
        assertThrows(SesionException.class, () -> servicio.agregarParte(99, SESION, 0, new byte[]{1}));
    }

    @Test
    void noSePuedeEmpezarPorUnaParteQueNoEsLaPrimera() {
        assertThrows(SesionException.class, () -> servicio.agregarParte(MENTOR, SESION, 3, new byte[]{1}));
    }

    @Test
    void conUnaGrabacionTerminadaNoSeAgregaMas() {
        servicio.agregarParte(MENTOR, SESION, 0, new byte[]{1});
        servicio.terminar(MENTOR, SESION, 1);

        assertThrows(SesionException.class, () -> servicio.agregarParte(MENTOR, SESION, 1, new byte[]{2}));
    }

    @Test
    void masDelTamanoMaximoSeRechaza() {
        assertThrows(SesionException.class, () -> servicio.agregarParte(MENTOR, SESION, 0, new byte[1024 * 1024 + 1]));
    }

    @Test
    void sinGrabacionListaNoSePuedeVer() {
        servicio.agregarParte(MENTOR, SESION, 0, new byte[]{1});

        assertThrows(SesionException.class, () -> servicio.archivoParaVer(ESTUDIANTE, SESION));
    }

    @Test
    void unExtranoNoVeLaGrabacion() {
        servicio.agregarParte(MENTOR, SESION, 0, new byte[]{1});
        servicio.terminar(MENTOR, SESION, 1);

        assertThrows(SesionException.class, () -> servicio.archivoParaVer(77, SESION));
    }
}
