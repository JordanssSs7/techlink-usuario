package com.techlink.exception;

import java.util.Map;

// HU-04: los cursos criticos tienen datos incorrectos (curso inexistente, inactivo o demasiados cursos). Responde 400.
public class CursosCriticosInvalidosException extends RuntimeException {

    private final Map<String, String> campos;

    public CursosCriticosInvalidosException(Map<String, String> campos) {
        super("Datos inválidos");
        this.campos = campos;
    }

    // Campo con error -> mensaje, con el mismo formato que las demas validaciones.
    public Map<String, String> getCampos() {
        return campos;
    }
}
