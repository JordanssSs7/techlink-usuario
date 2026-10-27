package com.techlink.exception;

// HU-02: Se lanza cuando la cuenta existe pero está inactiva (por ejemplo, sin verificar) (responde 403).
public class CuentaInactivaException extends RuntimeException {
    public CuentaInactivaException(String message) {
        super(message);
    }
}
