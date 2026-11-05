package com.techlink.exception;

// HU-04: la IA no pudo responder (sin clave, sin internet, limite de uso, respuesta invalida...).
// No llega al usuario: el agente usa el calculo base en su lugar.
public class IaNoDisponibleException extends RuntimeException {
    public IaNoDisponibleException(String message) {
        super(message);
    }

    public IaNoDisponibleException(String message, Throwable causa) {
        super(message, causa);
    }
}
