package com.example.multas.domain.port;

import com.example.multas.domain.ResultadoPago;
import com.example.multas.model.Multa;

// domain/port/PasarelaPagoPort.java — Puerto de salida hexagonal hacia pasarelas de pago
public interface PasarelaPagoPort {
    ResultadoPago procesar(Multa multa);
}
