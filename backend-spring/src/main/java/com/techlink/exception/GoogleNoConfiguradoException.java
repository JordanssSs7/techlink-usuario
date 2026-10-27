package com.techlink.exception;

// HU-02: se lanza cuando el servidor no tiene definido GOOGLE_CLIENT_ID (responde 503).
public class GoogleNoConfiguradoException extends RuntimeException {
    public GoogleNoConfiguradoException(String message) {
        super(message);
    }
}
