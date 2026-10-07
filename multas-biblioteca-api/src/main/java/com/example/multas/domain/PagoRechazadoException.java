package com.example.multas.domain;

// domain/PagoRechazadoException.java
public class PagoRechazadoException extends RuntimeException {
    public PagoRechazadoException(String mensaje) {
        super(mensaje);
    }
}
