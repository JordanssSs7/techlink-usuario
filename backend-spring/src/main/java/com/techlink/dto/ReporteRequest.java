package com.techlink.dto;

// HU-29: lo que manda el usuario al reportar. tipo = USUARIO (con idUsuario) o SESION (con idSesion).
public record ReporteRequest(String tipo, Integer idSesion, Integer idUsuario, String motivo, String descripcion) {
}
