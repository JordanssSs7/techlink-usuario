package com.techlink.service;

import com.techlink.dto.PostulacionResponse;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Estudiante;
import com.techlink.model.Postulacion;
import com.techlink.model.PostulacionArchivo;
import com.techlink.repository.EstudianteRepository;
import com.techlink.repository.PostulacionArchivoRepository;
import com.techlink.repository.PostulacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-03: postular a mentor con el comprobante de cursos aprobados.
class PostulacionServiceTest {

    private EstudianteRepository estudianteRepository;
    private PostulacionRepository postulacionRepository;
    private PostulacionArchivoRepository archivoRepository;
    private ApplicationEventPublisher eventos;
    private PostulacionService servicio;
    private Estudiante estudiante;

    @BeforeEach
    void preparar() {
        estudianteRepository = Mockito.mock(EstudianteRepository.class);
        postulacionRepository = Mockito.mock(PostulacionRepository.class);
        archivoRepository = Mockito.mock(PostulacionArchivoRepository.class);
        eventos = Mockito.mock(ApplicationEventPublisher.class);
        servicio = new PostulacionService(estudianteRepository, postulacionRepository, archivoRepository, eventos, 4);

        estudiante = new Estudiante();
        estudiante.setId(7);
        estudiante.setCiclo(5);
        estudiante.setUsuario(PruebaSesiones.usuario(10, "Ana", "Quispe", "ana@tecsup.edu.pe"));

        when(estudianteRepository.findByUsuarioId(10)).thenReturn(Optional.of(estudiante));
        when(postulacionRepository.findFirstByEstudianteIdOrderByFechaEnvioDescIdDesc(7)).thenReturn(Optional.empty());
        when(postulacionRepository.saveAndFlush(any(Postulacion.class))).thenAnswer(i -> {
            Postulacion p = i.getArgument(0);
            p.setId(99);
            return p;
        });
    }

    private static MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, "application/pdf", contenido);
    }

    private void ultimaPostulacion(Postulacion.Estado estado) {
        Postulacion p = new Postulacion();
        p.setId(50);
        p.setEstado(estado);
        p.setNombreArchivo("anterior.pdf");
        when(postulacionRepository.findFirstByEstudianteIdOrderByFechaEnvioDescIdDesc(7)).thenReturn(Optional.of(p));
    }

    @Test
    void guardaLaPostulacionEnRevisionConSuComprobanteYAvisa() {
        when(postulacionRepository.findFirstByEstudianteIdOrderByFechaEnvioDescIdDesc(7))
                .thenReturn(Optional.empty())
                .thenAnswer(i -> {
                    Postulacion p = new Postulacion();
                    p.setId(99);
                    p.setEstado(Postulacion.Estado.EN_REVISION);
                    p.setNombreArchivo("constancia.pdf");
                    return Optional.of(p);
                });

        PostulacionResponse r = servicio.postular(10, archivo("C:\\docs\\constancia.pdf", ArchivoSubidoTest.PDF), " Tengo todo aprobado ");

        ArgumentCaptor<Postulacion> guardada = ArgumentCaptor.forClass(Postulacion.class);
        verify(postulacionRepository).saveAndFlush(guardada.capture());
        assertEquals(Postulacion.Estado.EN_REVISION, guardada.getValue().getEstado());
        assertEquals("constancia.pdf", guardada.getValue().getNombreArchivo());
        assertEquals("application/pdf", guardada.getValue().getTipoContenido());
        assertEquals(ArchivoSubidoTest.PDF.length, guardada.getValue().getTamanoBytes());
        assertEquals("Tengo todo aprobado", guardada.getValue().getComentario());

        ArgumentCaptor<PostulacionArchivo> contenido = ArgumentCaptor.forClass(PostulacionArchivo.class);
        verify(archivoRepository).save(contenido.capture());
        assertEquals(99, contenido.getValue().getIdPostulacion());
        assertArrayEquals(ArchivoSubidoTest.PDF, contenido.getValue().getContenido());

        ArgumentCaptor<PostulacionRecibidaEvent> evento = ArgumentCaptor.forClass(PostulacionRecibidaEvent.class);
        verify(eventos).publishEvent(evento.capture());
        assertEquals("ana@tecsup.edu.pe", evento.getValue().correo());
        assertEquals("EN_REVISION", r.estado());
    }

    @Test
    void aceptaImagenesPngYJpg() {
        assertDoesNotThrow(() -> servicio.postular(10, archivo("a.png", ArchivoSubidoTest.PNG), null));
        assertDoesNotThrow(() -> servicio.postular(10, archivo("b.jpg", ArchivoSubidoTest.JPG), null));
    }

    @Test
    void rechazaUnArchivoQueNoEsPdfNiImagen() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> servicio.postular(10, archivo("virus.pdf", ArchivoSubidoTest.EXE), null));

        assertTrue(e.getCampos().containsKey("archivo"));
        verify(postulacionRepository, never()).saveAndFlush(any());
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void rechazaUnArchivoVacioOMasDe5Mb() {
        assertThrows(DatosInvalidosException.class, () -> servicio.postular(10, archivo("a.pdf", new byte[0]), null));

        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(ArchivoSubidoTest.PDF, 0, grande, 0, ArchivoSubidoTest.PDF.length);
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> servicio.postular(10, archivo("grande.pdf", grande), null));

        assertTrue(e.getCampos().get("archivo").contains("5 MB"));
    }

    @Test
    void elComentarioNoPuedeSuperar500Caracteres() {
        assertThrows(DatosInvalidosException.class,
                () -> servicio.postular(10, archivo("a.pdf", ArchivoSubidoTest.PDF), "a".repeat(501)));
    }

    @Test
    void soloPostulanLosDeCiclosAvanzados() {
        estudiante.setCiclo(2);

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.postular(10, archivo("a.pdf", ArchivoSubidoTest.PDF), null));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
        assertTrue(e.getMessage().contains("ciclo 4"));
    }

    @Test
    void unEstudianteSinCicloNoPuedePostular() {
        estudiante.setCiclo(null);

        assertThrows(ReglaNegocioException.class, () -> servicio.postular(10, archivo("a.pdf", ArchivoSubidoTest.PDF), null));
    }

    @Test
    void noPuedeTenerDosPostulacionesEnRevision() {
        ultimaPostulacion(Postulacion.Estado.EN_REVISION);

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.postular(10, archivo("a.pdf", ArchivoSubidoTest.PDF), null));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
        assertTrue(e.getMessage().contains("en revisión"));
    }

    @Test
    void unaPostulacionAprobadaNoSePuedeRepetir() {
        ultimaPostulacion(Postulacion.Estado.APROBADA);

        assertThrows(ReglaNegocioException.class, () -> servicio.postular(10, archivo("a.pdf", ArchivoSubidoTest.PDF), null));
    }

    @Test
    void despuesDeUnRechazoPuedePostularDeNuevo() {
        ultimaPostulacion(Postulacion.Estado.RECHAZADA);

        assertDoesNotThrow(() -> servicio.postular(10, archivo("a.pdf", ArchivoSubidoTest.PDF), null));
    }

    @Test
    void sinPerfilDeEstudianteEsNotFound() {
        when(estudianteRepository.findByUsuarioId(77)).thenReturn(Optional.empty());

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class, () -> servicio.estado(77));

        assertEquals(HttpStatus.NOT_FOUND, e.getEstado());
    }

    // ---------- estado ----------

    @Test
    void sinPostulacionesElEstadoEsNingunaYPuedePostular() {
        PostulacionResponse r = servicio.estado(10);

        assertEquals("NINGUNA", r.estado());
        assertTrue(r.puedePostular());
        assertNull(r.mensaje());
    }

    @Test
    void siEstaEnRevisionNoPuedePostularYSeLeExplica() {
        ultimaPostulacion(Postulacion.Estado.EN_REVISION);

        PostulacionResponse r = servicio.estado(10);

        assertEquals("EN_REVISION", r.estado());
        assertFalse(r.puedePostular());
        assertNotNull(r.mensaje());
    }

    @Test
    void unEstudianteDePrimerosCiclosVeElMotivo() {
        estudiante.setCiclo(1);

        PostulacionResponse r = servicio.estado(10);

        assertFalse(r.puedePostular());
        assertTrue(r.mensaje().contains("ciclo 4"));
    }

    @Test
    void siFueRechazadaMuestraElMotivoYPuedeIntentarOtraVez() {
        ultimaPostulacion(Postulacion.Estado.RECHAZADA);

        PostulacionResponse r = servicio.estado(10);

        assertEquals("RECHAZADA", r.estado());
        assertTrue(r.puedePostular());
    }
}
