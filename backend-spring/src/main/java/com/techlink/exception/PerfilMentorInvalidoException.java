package com.techlink.exception;

import java.util.Map;

// HU-05: el perfil del mentor tiene datos incorrectos (curso inexistente, horas al reves, horarios cruzados). Responde 400.
public class PerfilMentorInvalidoException extends RuntimeException {

    private final Map<String, String> campos;

    public PerfilMentorInvalidoException(Map<String, String> campos) {
        super("Datos inválidos");
        this.campos = campos;
    }

    // Campo con error -> mensaje, con el mismo formato que las demas validaciones.
    public Map<String, String> getCampos() {
        return campos;
    }
}
