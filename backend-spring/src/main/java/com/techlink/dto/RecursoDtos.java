package com.techlink.dto;

import java.time.LocalDateTime;

// HU-18: los datos que entran y salen de los recursos compartidos en una sesión.
public final class RecursoDtos {

    private RecursoDtos() {
    }

    public record NuevoEnlace(String titulo, String descripcion, String url) {
    }

    public record NuevoCodigo(String titulo, String descripcion, String lenguaje, String codigo) {
    }

    // tipo: ARCHIVO, ENLACE o CODIGO. Solo viene lo que corresponde al tipo (url, codigo o los datos del archivo).
    // "oculto" y "motivoModeracion" solo los ve el mentor: al estudiante no se le devuelve un recurso oculto.
    public record RecursoResponse(Integer id, String tipo, String titulo, String descripcion, String url, String codigo,
                                  String lenguaje, String nombreArchivo, String tipoContenido, Integer tamanoBytes,
                                  LocalDateTime fecha, boolean oculto, String motivoModeracion, boolean puedeEliminar) {
    }

    // Un archivo listo para descargar.
    public record Descarga(String nombre, String tipoContenido, byte[] contenido) {
    }
}
