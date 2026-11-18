package com.techlink.exception;

import org.springframework.http.HttpStatus;

// HU-03, HU-13, HU-14 y HU-15: una regla del negocio no se cumple (por ejemplo, ya tienes una postulación en revisión).
// Lleva el código HTTP que corresponde y un mensaje que se le muestra al usuario.
public class ReglaNegocioException extends RuntimeException {

    private final HttpStatus estado;

    public ReglaNegocioException(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() {
        return estado;
    }
}
