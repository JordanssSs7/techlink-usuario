package com.techlink.service;

import com.techlink.exception.DatosInvalidosException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

// HU-13 y HU-03: revisa un archivo subido. El tipo se deduce de los primeros bytes (no del nombre ni de lo que diga el
// navegador), porque cualquiera puede renombrar un archivo.
final class ArchivoSubido {

    static final String PDF = "application/pdf";
    static final String PNG = "image/png";
    static final String JPG = "image/jpeg";

    private ArchivoSubido() {
    }

    // Lee el archivo y comprueba que no esté vacío, que no pase de "maxBytes" y que sea de un tipo permitido.
    static byte[] leerYValidar(MultipartFile archivo, String campo, long maxBytes, Set<String> tiposPermitidos,
                               String descripcionTipos) {
        if (archivo == null || archivo.isEmpty()) {
            throw new DatosInvalidosException(campo, "Adjunta un archivo.");
        }
        if (archivo.getSize() > maxBytes) {
            throw new DatosInvalidosException(campo, "El archivo supera el máximo de " + (maxBytes / (1024 * 1024)) + " MB.");
        }
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new DatosInvalidosException(campo, "No se pudo leer el archivo. Inténtalo de nuevo.");
        }
        String tipo = detectarTipo(bytes);
        if (tipo == null || !tiposPermitidos.contains(tipo)) {
            throw new DatosInvalidosException(campo, "Formato no permitido: sube " + descripcionTipos + ".");
        }
        return bytes;
    }

    // PDF empieza con "%PDF-", PNG con 89 50 4E 47 y JPEG con FF D8 FF. Si no coincide, devuelve null.
    static String detectarTipo(byte[] b) {
        if (b == null || b.length < 4) {
            return null;
        }
        if (b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return PDF;
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return PNG;
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return JPG;
        }
        return null;
    }

    // Nombre seguro para guardar: sin carpetas, sin caracteres raros y de largo limitado.
    static String nombreSeguro(String original, String porDefecto) {
        if (original == null || original.isBlank()) {
            return porDefecto;
        }
        String soloNombre = original.replace('\\', '/');
        soloNombre = soloNombre.substring(soloNombre.lastIndexOf('/') + 1);
        String limpio = soloNombre.replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (limpio.isEmpty()) {
            return porDefecto;
        }
        return limpio.length() > 120 ? limpio.substring(limpio.length() - 120) : limpio;
    }
}
