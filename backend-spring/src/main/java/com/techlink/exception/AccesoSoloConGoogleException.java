package com.techlink.exception;

// HU-02: se lanza cuando un alumno o mentor intenta entrar con correo y contraseña; deben usar Google (responde 403).
public class AccesoSoloConGoogleException extends RuntimeException {
    public AccesoSoloConGoogleException(String message) {
        super(message);
    }
}
