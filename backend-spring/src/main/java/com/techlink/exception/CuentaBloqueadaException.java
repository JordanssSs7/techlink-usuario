package com.techlink.exception;

// HU-02: Se lanza cuando la cuenta está bloqueada temporalmente por intentos fallidos (responde 423).
public class CuentaBloqueadaException extends RuntimeException {

    private final long minutosRestantes;

    public CuentaBloqueadaException(String message, long minutosRestantes) {
        super(message);
        this.minutosRestantes = minutosRestantes;
    }

    public long getMinutosRestantes() {
        return minutosRestantes;
    }
}
