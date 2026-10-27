package com.techlink.exception;

// HU-02 (Google): se lanza cuando la cuenta de Google no es del dominio institucional (responde 403).
public class CorreoNoInstitucionalException extends RuntimeException {
    public CorreoNoInstitucionalException(String message) {
        super(message);
    }
}
