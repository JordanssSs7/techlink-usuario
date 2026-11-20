package com.techlink.service;

import com.techlink.dto.RecursoDtos.Descarga;
import com.techlink.dto.RecursoDtos.NuevoCodigo;
import com.techlink.dto.RecursoDtos.NuevoEnlace;
import com.techlink.dto.RecursoDtos.RecursoResponse;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Recurso;
import com.techlink.model.RecursoArchivo;
import com.techlink.model.Sesion;
import com.techlink.repository.RecursoArchivoRepository;
import com.techlink.repository.RecursoRepository;
import com.techlink.repository.SesionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.techlink.service.PruebaSesiones.USUARIO_ESTUDIANTE;
import static com.techlink.service.PruebaSesiones.USUARIO_MENTOR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-18: el mentor comparte recursos en una sesión y el estudiante los ve y descarga.
class RecursoServiceTest {

    private RecursoRepository recursoRepository;
    private RecursoArchivoRepository archivoRepository;
    private SesionRepository sesionRepository;
    private RecursoService servicio;
    private Sesion sesion;
    private final List<Recurso> guardados = new ArrayList<>();

    @BeforeEach
    void preparar() {
        recursoRepository = Mockito.mock(RecursoRepository.class);
        archivoRepository = Mockito.mock(RecursoArchivoRepository.class);
        sesionRepository = Mockito.mock(SesionRepository.class);
        servicio = new RecursoService(recursoRepository, archivoRepository, sesionRepository);

        sesion = PruebaSesiones.sesion(1, LocalDateTime.now().minusHours(3), 60, Sesion.EstadoSesion.CONFIRMADA);
        when(sesionRepository.findById(1)).thenReturn(Optional.of(sesion));
        when(recursoRepository.findBySesionIdOrderByFechaCreacionDescIdDesc(1)).thenAnswer(i -> guardados);
        when(recursoRepository.save(any(Recurso.class))).thenAnswer(i -> {
            Recurso r = i.getArgument(0);
            r.setId(100 + guardados.size());
            guardados.add(r);
            return r;
        });
        when(recursoRepository.saveAndFlush(any(Recurso.class))).thenAnswer(i -> {
            Recurso r = i.getArgument(0);
            r.setId(100 + guardados.size());
            guardados.add(r);
            return r;
        });
        when(recursoRepository.findById(any())).thenAnswer(i -> guardados.stream().filter(r -> r.getId().equals(i.getArgument(0))).findFirst());
    }

    private static DatosInvalidosException invalido(Runnable accion) {
        return assertThrows(DatosInvalidosException.class, accion::run);
    }

    private Recurso recursoGuardado(Recurso.Tipo tipo, boolean oculto) {
        Recurso r = new Recurso();
        r.setId(100 + guardados.size());
        r.setSesion(sesion);
        r.setAutor(sesion.getMentor().getUsuario());
        r.setTipo(tipo);
        r.setTitulo("Guía de JOIN");
        r.setFechaCreacion(LocalDateTime.now());
        if (oculto) {
            ReflectionTestUtils.setField(r, "oculto", true);
            ReflectionTestUtils.setField(r, "motivoModeracion", "Contenido ajeno al curso");
        }
        guardados.add(r);
        return r;
    }

    // ---------- enlace ----------

    @Test
    void elMentorComparteUnEnlaceHttps() {
        RecursoResponse r = servicio.agregarEnlace(USUARIO_MENTOR, 1, new NuevoEnlace("  Documentación  ", "Para repasar", " https://dev.mysql.com/doc/ "));

        assertEquals("ENLACE", r.tipo());
        assertEquals("Documentación", r.titulo());
        assertEquals("https://dev.mysql.com/doc/", r.url());
        assertEquals(USUARIO_MENTOR, guardados.get(0).getAutor().getId());
        assertTrue(r.puedeEliminar());
    }

    @Test
    void soloAceptaEnlacesHttpOHttpsValidos() {
        for (String malo : new String[]{null, "", "   ", "javascript:alert(1)", "ftp://sitio.com/a", "file:///c:/x", "sin-esquema.com",
                "https://", "http://con espacio.com", "https://" + "a".repeat(500) + ".com"}) {
            assertTrue(invalido(() -> servicio.agregarEnlace(USUARIO_MENTOR, 1, new NuevoEnlace("Titulo", null, malo)))
                    .getCampos().containsKey("url"), "debía rechazar: " + malo);
        }
        assertDoesNotThrow(() -> servicio.agregarEnlace(USUARIO_MENTOR, 1, new NuevoEnlace("Titulo", null, "http://localhost:8080/a?b=c")));
    }

    @Test
    void elTituloEsObligatorioYLaDescripcionTieneLimite() {
        assertTrue(invalido(() -> servicio.agregarEnlace(USUARIO_MENTOR, 1, new NuevoEnlace("ab", null, "https://a.com"))).getCampos().containsKey("titulo"));
        assertTrue(invalido(() -> servicio.agregarEnlace(USUARIO_MENTOR, 1, new NuevoEnlace(null, null, "https://a.com"))).getCampos().containsKey("titulo"));
        assertTrue(invalido(() -> servicio.agregarEnlace(USUARIO_MENTOR, 1, new NuevoEnlace("Titulo", "d".repeat(301), "https://a.com"))).getCampos().containsKey("descripcion"));
    }

    // ---------- código ----------

    @Test
    void elMentorComparteUnFragmentoDeCodigo() {
        RecursoResponse r = servicio.agregarCodigo(USUARIO_MENTOR, 1, new NuevoCodigo("Ejemplo JOIN", null, "SQL", "SELECT * FROM a JOIN b ON a.id = b.id;"));

        assertEquals("CODIGO", r.tipo());
        assertEquals("SQL", r.lenguaje());
        assertTrue(r.codigo().startsWith("SELECT"));
    }

    @Test
    void elCodigoNoPuedeEstarVacioNiSerEnorme() {
        assertTrue(invalido(() -> servicio.agregarCodigo(USUARIO_MENTOR, 1, new NuevoCodigo("Titulo", null, null, "   "))).getCampos().containsKey("codigo"));
        assertTrue(invalido(() -> servicio.agregarCodigo(USUARIO_MENTOR, 1, new NuevoCodigo("Titulo", null, null, "x".repeat(5001)))).getCampos().containsKey("codigo"));
        assertTrue(invalido(() -> servicio.agregarCodigo(USUARIO_MENTOR, 1, new NuevoCodigo("Titulo", null, "l".repeat(31), "x"))).getCampos().containsKey("lenguaje"));
    }

    // ---------- archivo ----------

    private static MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, "application/octet-stream", contenido);
    }

    @Test
    void elMentorSubeUnPdfDeHasta10Mb() {
        RecursoResponse r = servicio.agregarArchivo(USUARIO_MENTOR, 1, "Guía", "PDF de la clase",
                archivo("C:\\docs\\guia.pdf", ArchivoSubidoTest.PDF));

        assertEquals("ARCHIVO", r.tipo());
        assertEquals("guia.pdf", r.nombreArchivo());
        assertEquals("application/pdf", r.tipoContenido());
        ArgumentCaptor<RecursoArchivo> contenido = ArgumentCaptor.forClass(RecursoArchivo.class);
        verify(archivoRepository).save(contenido.capture());
        assertArrayEquals(ArchivoSubidoTest.PDF, contenido.getValue().getContenido());
    }

    @Test
    void aceptaUnArchivoDe9Mb() {
        byte[] grande = new byte[9 * 1024 * 1024];
        System.arraycopy(ArchivoSubidoTest.PDF, 0, grande, 0, ArchivoSubidoTest.PDF.length);

        assertDoesNotThrow(() -> servicio.agregarArchivo(USUARIO_MENTOR, 1, "Guía grande", null, archivo("g.pdf", grande)));
    }

    @Test
    void rechazaMasDe10MbYArchivosQueNoSonPdfNiImagen() {
        byte[] enorme = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(ArchivoSubidoTest.PDF, 0, enorme, 0, ArchivoSubidoTest.PDF.length);

        assertTrue(invalido(() -> servicio.agregarArchivo(USUARIO_MENTOR, 1, "Enorme", null, archivo("e.pdf", enorme)))
                .getCampos().get("archivo").contains("10 MB"));
        assertTrue(invalido(() -> servicio.agregarArchivo(USUARIO_MENTOR, 1, "Virus", null, archivo("a.pdf", ArchivoSubidoTest.EXE)))
                .getCampos().containsKey("archivo"));
        verify(archivoRepository, never()).save(any());
    }

    // ---------- quién puede ----------

    @Test
    void soloElMentorDeLaSesionComparteRecursos() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ReglaNegocioException.class,
                () -> servicio.agregarEnlace(USUARIO_ESTUDIANTE, 1, new NuevoEnlace("Titulo", null, "https://a.com"))).getEstado());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ReglaNegocioException.class,
                () -> servicio.agregarEnlace(999, 1, new NuevoEnlace("Titulo", null, "https://a.com"))).getEstado());
    }

    @Test
    void noSeComparteEnUnaSesionCancelada() {
        sesion.setEstado(Sesion.EstadoSesion.CANCELADA);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ReglaNegocioException.class,
                () -> servicio.agregarEnlace(USUARIO_MENTOR, 1, new NuevoEnlace("Titulo", null, "https://a.com"))).getEstado());
    }

    @Test
    void unaSesionAdmiteHasta20Recursos() {
        for (int i = 0; i < 20; i++) {
            recursoGuardado(Recurso.Tipo.ENLACE, false);
        }

        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> servicio.agregarEnlace(USUARIO_MENTOR, 1, new NuevoEnlace("Titulo", null, "https://a.com")));

        assertEquals(HttpStatus.CONFLICT, e.getEstado());
    }

    // ---------- listar ----------

    @Test
    void elEstudianteVeLosRecursosDeSuSesionSinLosOcultos() {
        recursoGuardado(Recurso.Tipo.ENLACE, false);
        recursoGuardado(Recurso.Tipo.CODIGO, true);

        List<RecursoResponse> r = servicio.listar(USUARIO_ESTUDIANTE, 1);

        assertEquals(1, r.size());
        assertFalse(r.get(0).oculto());
        assertFalse(r.get(0).puedeEliminar());
    }

    @Test
    void elMentorVeSusRecursosOcultosConSuMotivo() {
        recursoGuardado(Recurso.Tipo.ENLACE, false);
        recursoGuardado(Recurso.Tipo.CODIGO, true);

        List<RecursoResponse> r = servicio.listar(USUARIO_MENTOR, 1);

        assertEquals(2, r.size());
        RecursoResponse oculto = r.stream().filter(RecursoResponse::oculto).findFirst().orElseThrow();
        assertEquals("Contenido ajeno al curso", oculto.motivoModeracion());
    }

    @Test
    void quienNoEsDeLaSesionNoVeLosRecursos() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ReglaNegocioException.class, () -> servicio.listar(555, 1)).getEstado());
    }

    // ---------- descargar y eliminar ----------

    @Test
    void elEstudianteDescargaElArchivo() {
        Recurso r = recursoGuardado(Recurso.Tipo.ARCHIVO, false);
        r.setNombreArchivo("guia.pdf");
        r.setTipoContenido("application/pdf");
        RecursoArchivo contenido = new RecursoArchivo();
        contenido.setIdRecurso(r.getId());
        contenido.setContenido(ArchivoSubidoTest.PDF);
        when(archivoRepository.findById(r.getId())).thenReturn(Optional.of(contenido));

        Descarga d = servicio.descargar(USUARIO_ESTUDIANTE, r.getId());

        assertEquals("guia.pdf", d.nombre());
        assertArrayEquals(ArchivoSubidoTest.PDF, d.contenido());
    }

    @Test
    void unArchivoOcultoSoloLoDescargaSuAutor() {
        Recurso r = recursoGuardado(Recurso.Tipo.ARCHIVO, true);
        r.setNombreArchivo("guia.pdf");
        r.setTipoContenido("application/pdf");
        RecursoArchivo contenido = new RecursoArchivo();
        contenido.setContenido(ArchivoSubidoTest.PDF);
        when(archivoRepository.findById(r.getId())).thenReturn(Optional.of(contenido));

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ReglaNegocioException.class,
                () -> servicio.descargar(USUARIO_ESTUDIANTE, r.getId())).getEstado());
        assertDoesNotThrow(() -> servicio.descargar(USUARIO_MENTOR, r.getId()));
    }

    @Test
    void soloLosRecursosDeTipoArchivoSeDescargan() {
        Recurso r = recursoGuardado(Recurso.Tipo.ENLACE, false);

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ReglaNegocioException.class,
                () -> servicio.descargar(USUARIO_ESTUDIANTE, r.getId())).getEstado());
    }

    @Test
    void elMentorQuitaUnRecursoSuyoYElEstudianteNo() {
        Recurso r = recursoGuardado(Recurso.Tipo.ENLACE, false);

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ReglaNegocioException.class,
                () -> servicio.eliminar(USUARIO_ESTUDIANTE, r.getId())).getEstado());
        servicio.eliminar(USUARIO_MENTOR, r.getId());

        verify(recursoRepository).delete(r);
    }
}
