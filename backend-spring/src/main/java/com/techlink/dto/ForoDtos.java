package com.techlink.dto;

import java.time.LocalDateTime;
import java.util.List;

// HU-17: los datos que entran y salen del foro de consultas.
public final class ForoDtos {

    private ForoDtos() {
    }

    // Lo que el usuario escribe al publicar una consulta o una respuesta.
    public record NuevaConsulta(Integer cursoId, String titulo, String contenido) {
    }

    // respuestaPadreId: la respuesta a la que se contesta (null = respuesta principal a la consulta).
    public record NuevaRespuesta(String contenido, Integer respuestaPadreId) {

        public NuevaRespuesta(String contenido) {
            this(contenido, null);
        }
    }

    // De quien escribe solo se muestra su primer nombre y primer apellido, y si es mentor.
    public record Autor(Integer id, String nombre, String rol) {
    }

    // Una consulta en la lista. "resuelta" es true si alguna respuesta fue marcada como útil.
    public record ConsultaResumen(Integer id, String titulo, String extracto, Integer cursoId, String curso, Autor autor,
                                  LocalDateTime fecha, int respuestas, boolean resuelta) {
    }

    // "esMia": quien consulta es el autor y por eso puede marcar una respuesta como útil.
    // padreId: null en una respuesta principal; en un hilo, el id de la respuesta principal.
    public record RespuestaResponse(Integer id, String contenido, Autor autor, LocalDateTime fecha, boolean util,
                                    boolean esMia, Integer padreId, boolean editada) {
    }

    public record ConsultaDetalle(Integer id, String titulo, String contenido, Integer cursoId, String curso, Autor autor,
                                  LocalDateTime fecha, boolean esMia, List<RespuestaResponse> respuestas, boolean editada) {
    }

    // Lo que manda quien edita su consulta o su respuesta.
    public record EdicionConsulta(String titulo, String contenido) {
    }

    public record EdicionRespuesta(String contenido) {
    }
}
