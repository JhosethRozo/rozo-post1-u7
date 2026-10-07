package com.example.multas;

import com.example.multas.domain.ResultadoPago;
import com.example.multas.domain.port.PasarelaPagoPort;
import com.example.multas.infrastructure.pago.PagosUdesAdapter;
import com.example.multas.infrastructure.pago.WompiAdapter;
import com.example.multas.model.EstadoMulta;
import com.example.multas.model.Multa;
import com.example.multas.repository.MultaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PagoEnLineaParte2Tests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PasarelaPagoPort pasarelaPagoPort;

    @MockBean
    private MultaRepository multaRepository;

    @Test
    @DisplayName("Punto de Decisión 4: Adaptador PagosUDES traduce formato específico a ResultadoPago neutro")
    void testPagosUdesAdapterTraduccion() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        PagosUdesAdapter adapter = new PagosUdesAdapter(restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "urlPasarela", "http://localhost:9001/pagosudes/transacciones");

        // Simular respuesta JSON PagosUDES
        server.expect(MockRestRequestMatchers.requestTo("http://localhost:9001/pagosudes/transacciones"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.content().json("""
                        {
                            "estudianteId": "EST-888",
                            "monto": 3000
                        }
                        """))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                            "idTransaccion": "TX-UDES-9999",
                            "estadoTransaccion": "APROBADA"
                        }
                        """, MediaType.APPLICATION_JSON));

        Multa multa = new Multa("EST-888", "Libro de Redes", 6);
        multa.setId(10L);

        ResultadoPago resultado = adapter.procesar(multa);

        assertTrue(resultado.exitoso());
        assertEquals("PAGOSUDES", resultado.proveedor());
        assertEquals("TX-UDES-9999", resultado.referenciaExterna());
        assertEquals("Pago aprobado por PagosUDES", resultado.mensaje());
        server.verify();
    }

    @Test
    @DisplayName("Punto de Decisión 4: Adaptador Wompi traduce formato en centavos y reference a ResultadoPago neutro")
    void testWompiAdapterTraduccion() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        WompiAdapter adapter = new WompiAdapter(restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(adapter, "urlPasarela", "http://localhost:9002/wompi/transactions");

        Multa multa = new Multa("EST-777", "Libro de Bases de Datos", 4); // 4 * 500 = 2000 COP -> 200000 centavos
        multa.setId(15L);

        // Simular respuesta JSON Wompi
        server.expect(MockRestRequestMatchers.requestTo("http://localhost:9002/wompi/transactions"))
                .andExpect(MockRestRequestMatchers.method(HttpMethod.POST))
                .andExpect(MockRestRequestMatchers.content().json("""
                        {
                            "reference": "multa-15",
                            "amountInCents": 200000
                        }
                        """))
                .andRespond(MockRestResponseCreators.withSuccess("""
                        {
                            "reference": "multa-15",
                            "status": "APPROVED"
                        }
                        """, MediaType.APPLICATION_JSON));

        ResultadoPago resultado = adapter.procesar(multa);

        assertTrue(resultado.exitoso());
        assertEquals("WOMPI", resultado.proveedor());
        assertEquals("multa-15", resultado.referenciaExterna());
        assertEquals("Pago aprobado por Wompi", resultado.mensaje());
        server.verify();
    }

    @Test
    @DisplayName("Checkpoint Parte 2: Pago en línea exitoso retorna 200 OK y marca multa como PAGADA con el proveedor")
    void testPagoEnLineaExitoso() throws Exception {
        Multa multa = new Multa("EST-501", "Libro Compiladores", 2);
        multa.setId(1L);

        when(multaRepository.findById(1L)).thenReturn(Optional.of(multa));
        when(pasarelaPagoPort.procesar(any(Multa.class)))
                .thenReturn(new ResultadoPago("PAGOSUDES", true, "TX-12345", "Pago aprobado por PagosUDES"));
        when(multaRepository.save(any(Multa.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(post("/api/multas/1/pagar-en-linea"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.estado", is("PAGADA")))
                .andExpect(jsonPath("$.metodoPago", is("PAGOSUDES")))
                .andExpect(jsonPath("$.fechaPago", notNullValue()));

        verify(pasarelaPagoPort, times(1)).procesar(multa);
    }

    @Test
    @DisplayName("Checkpoint Parte 2: Pago rechazado por la pasarela retorna 402 PAYMENT_REQUIRED con mensaje del proveedor")
    void testPagoEnLineaRechazado() throws Exception {
        Multa multa = new Multa("EST-502", "Libro Inteligencia Artificial", 3);
        multa.setId(2L);

        when(multaRepository.findById(2L)).thenReturn(Optional.of(multa));
        when(pasarelaPagoPort.procesar(any(Multa.class)))
                .thenReturn(new ResultadoPago("PAGOSUDES", false, null, "Transaccion rechazada por fondos insuficientes"));

        mockMvc.perform(post("/api/multas/2/pagar-en-linea"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error", is("Transaccion rechazada por fondos insuficientes")));

        assertEquals(EstadoMulta.PENDIENTE, multa.getEstado(), "La multa debe permanecer PENDIENTE si el pago fue rechazado");
    }

    @Test
    @DisplayName("Checkpoint Parte 2: Pagar en línea una multa ya pagada retorna 409 Conflict")
    void testPagoEnLineaMultaYaPagada() throws Exception {
        Multa multa = new Multa("EST-503", "Libro Redes II", 1);
        multa.setId(3L);
        multa.marcarComoPagada("VENTANILLA");

        when(multaRepository.findById(3L)).thenReturn(Optional.of(multa));

        mockMvc.perform(post("/api/multas/3/pagar-en-linea"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("ya fue pagada")));

        verify(pasarelaPagoPort, never()).procesar(any());
    }

    @Test
    @DisplayName("Verificación de desacoplamiento: domain no importa Spring ni librerías externas")
    void testDominioPuroSinSpring() {
        assertDoesNotThrow(() -> {
            Class<?> portClass = Class.forName("com.example.multas.domain.port.PasarelaPagoPort");
            Class<?> resultClass = Class.forName("com.example.multas.domain.ResultadoPago");
            Class<?> exClass = Class.forName("com.example.multas.domain.PagoRechazadoException");

            for (Class<?> clazz : new Class<?>[]{portClass, resultClass, exClass}) {
                for (java.lang.annotation.Annotation ann : clazz.getAnnotations()) {
                    assertFalse(ann.annotationType().getName().startsWith("org.springframework"),
                            "La clase de dominio " + clazz.getSimpleName() + " no debe tener anotaciones de Spring");
                }
            }
        });
    }
}
