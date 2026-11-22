package com.techlink.dto;

// HU-29: lo que manda el usuario al reportar. tipo = USUARIO (con idUsuario), SESION (con idSesion), CONSULTA (con idConsulta) o
// RESPUESTA (con idRespuesta): las dos últimas son del foro.
public record ReporteRequest(String tipo, Integer idSesion, Integer idUsuario, String motivo, String descripcion,
                             Integer idConsulta, Integer idRespuesta) {

    public ReporteRequest(String tipo, Integer idSesion, Integer idUsuario, String motivo, String descripcion) {
        this(tipo, idSesion, idUsuario, motivo, descripcion, null, null);
    }
}
