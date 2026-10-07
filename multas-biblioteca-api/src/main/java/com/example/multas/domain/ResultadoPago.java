package com.example.multas.domain;

// domain/ResultadoPago.java — tipo de dominio: ninguna pasarela concreta
// se expone hacia MultaService, solo este contrato común.
public record ResultadoPago(
    String proveedor,
    boolean exitoso,
    String referenciaExterna,
    String mensaje
) {}
