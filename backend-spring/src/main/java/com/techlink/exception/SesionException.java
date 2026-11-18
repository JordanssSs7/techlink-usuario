package com.techlink.exception;

import org.springframework.http.HttpStatus;

// HU-09, HU-19, HU-20 y HU-21: una regla de la sesión no se cumple (no existe, ya pasó, plazo vencido...).
// Lleva el código HTTP que corresponde y un mensaje que se le muestra al usuario.
public class SesionException extends RuntimeException {

    private final HttpStatus estado;

    public SesionException(HttpStatus estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public HttpStatus getEstado() {
        return estado;
    }
}
