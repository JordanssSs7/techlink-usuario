package com.techlink.exception;

// HU-02 (Google): se lanza cuando alguien entra con Google pero aun no tiene cuenta en TechLink (responde 404).
public class UsuarioNoRegistradoException extends RuntimeException {
    public UsuarioNoRegistradoException(String message) {
        super(message);
    }
}
