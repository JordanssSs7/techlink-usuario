package com.techlink.service;

import com.techlink.exception.DatosInvalidosException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// Pruebas de la revisión de archivos subidos (HU-03 y HU-13): el tipo se deduce de los bytes, no del nombre.
class ArchivoSubidoTest {

    static final byte[] PDF = "%PDF-1.4 contenido de prueba".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    static final byte[] TEXTO = "esto es solo texto".getBytes(StandardCharsets.UTF_8);
    static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};

    private static MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, "application/octet-stream", contenido);
    }

    @Test
    void reconoceElTipoPorLosPrimerosBytes() {
        assertEquals(ArchivoSubido.PDF, ArchivoSubido.detectarTipo(PDF));
        assertEquals(ArchivoSubido.PNG, ArchivoSubido.detectarTipo(PNG));
        assertEquals(ArchivoSubido.JPG, ArchivoSubido.detectarTipo(JPG));
        assertNull(ArchivoSubido.detectarTipo(TEXTO));
        assertNull(ArchivoSubido.detectarTipo(EXE));
        assertNull(ArchivoSubido.detectarTipo(new byte[]{1, 2}));
        assertNull(ArchivoSubido.detectarTipo(null));
    }

    @Test
    void aceptaUnArchivoPermitidoDentroDelLimite() {
        byte[] r = ArchivoSubido.leerYValidar(archivo("nota.pdf", PDF), "archivo", 1024,
                Set.of(ArchivoSubido.PDF), "un PDF");

        assertArrayEquals(PDF, r);
    }

    @Test
    void rechazaUnArchivoRenombradoQueNoEsDelTipoQueDice() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () ->
                ArchivoSubido.leerYValidar(archivo("comprobante.pdf", EXE), "archivo", 1024,
                        Set.of(ArchivoSubido.PDF, ArchivoSubido.PNG), "un PDF o PNG"));

        assertTrue(e.getCampos().get("archivo").contains("Formato no permitido"));
    }

    @Test
    void rechazaUnTipoValidoPeroNoPermitidoEnEseCampo() {
        assertThrows(DatosInvalidosException.class, () ->
                ArchivoSubido.leerYValidar(archivo("foto.pdf", PDF), "foto", 1024,
                        Set.of(ArchivoSubido.PNG, ArchivoSubido.JPG), "una imagen"));
    }

    @Test
    void rechazaUnArchivoVacioONulo() {
        assertThrows(DatosInvalidosException.class, () ->
                ArchivoSubido.leerYValidar(archivo("a.pdf", new byte[0]), "archivo", 1024, Set.of(ArchivoSubido.PDF), "un PDF"));
        assertThrows(DatosInvalidosException.class, () ->
                ArchivoSubido.leerYValidar(null, "archivo", 1024, Set.of(ArchivoSubido.PDF), "un PDF"));
    }

    @Test
    void rechazaUnArchivoMasGrandeQueElMaximo() {
        byte[] grande = new byte[3 * 1024 * 1024];
        System.arraycopy(PDF, 0, grande, 0, PDF.length);

        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () ->
                ArchivoSubido.leerYValidar(archivo("grande.pdf", grande), "archivo", 2L * 1024 * 1024,
                        Set.of(ArchivoSubido.PDF), "un PDF"));

        assertTrue(e.getCampos().get("archivo").contains("2 MB"));
    }

    @Test
    void elNombreSeGuardaSinCarpetasNiCaracteresRaros() {
        assertEquals("constancia.pdf", ArchivoSubido.nombreSeguro("C:\\Users\\Ana\\Documentos\\constancia.pdf", "x"));
        assertEquals("passwd", ArchivoSubido.nombreSeguro("../../etc/passwd", "x"));
        assertEquals("a_b_c.pdf", ArchivoSubido.nombreSeguro("a<b>c.pdf", "x"));
        assertEquals("comprobante", ArchivoSubido.nombreSeguro("  ", "comprobante"));
        assertEquals("comprobante", ArchivoSubido.nombreSeguro(null, "comprobante"));
        assertTrue(ArchivoSubido.nombreSeguro("a".repeat(300) + ".pdf", "x").length() <= 120);
    }
}
