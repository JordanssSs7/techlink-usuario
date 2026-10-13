package com.techlink.exception;

// Se lanza cuando el correo no es del dominio institucional (responde 400).
public class DominioInvalidoException extends RuntimeException {
    public DominioInvalidoException(String message) {
        super(message);
    }
}
