package com.techlink.exception;

// Se lanza cuando el correo ya esta registrado (responde 409).
public class CorreoDuplicadoException extends RuntimeException {
    public CorreoDuplicadoException(String message) {
        super(message);
    }
}
