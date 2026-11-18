package com.techlink.exception;

import java.util.Map;

// HU-13 y HU-03: los datos enviados no son válidos. Responde 400 con el detalle por campo ("campos").
public class DatosInvalidosException extends RuntimeException {

    private final Map<String, String> campos;

    public DatosInvalidosException(Map<String, String> campos) {
        super("Datos inválidos");
        this.campos = campos;
    }

    public DatosInvalidosException(String campo, String mensaje) {
        this(Map.of(campo, mensaje));
    }

    public Map<String, String> getCampos() {
        return campos;
    }
}
