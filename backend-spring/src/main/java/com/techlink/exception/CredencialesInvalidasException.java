package com.techlink.exception;

// HU-02: Se lanza cuando el correo o la contraseña son incorrectos (responde 401).
public class CredencialesInvalidasException extends RuntimeException {
    public CredencialesInvalidasException(String message) {
        super(message);
    }
}
