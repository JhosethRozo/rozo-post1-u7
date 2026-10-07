package com.example.multas;

import com.example.multas.model.EstadoMulta;
import com.example.multas.model.Multa;
import com.example.multas.repository.MultaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MultasApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MultaRepository multaRepository;

    @BeforeEach
    void setUp() {
        multaRepository.deleteAll();
    }

    @Test
    @DisplayName("Punto de Decisión 1: Cálculo puro en la entidad Multa con tope máximo")
    void testCalculoMontoEnEntidad() {
        // 5 días * 500 = 2500
        assertEquals(new BigDecimal("2500"), Multa.calcularMonto(5));
        // 30 días * 500 = 15000 (justo el tope)
        assertEquals(new BigDecimal("15000"), Multa.calcularMonto(30));
        // 40 días * 500 = 20000 -> acotado a 15000
        assertEquals(new BigDecimal("15000"), Multa.calcularMonto(40));
    }

    @Test
    @DisplayName("Checkpoint 1: GET /api/multas retorna lista vacía (200 OK) al inicio")
    void testListarMultasVacia() throws Exception {
        mockMvc.perform(get("/api/multas"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Checkpoint 2: POST /api/multas con JSON válido retorna 201 Created y monto calculado")
    void testGenerarMultaValida() throws Exception {
        String json = """
                {
                    "estudianteId": "EST-101",
                    "concepto": "Devolución tardía de Clean Code",
                    "diasAtraso": 4
                }
                """;

        mockMvc.perform(post("/api/multas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.estudianteId", is("EST-101")))
                .andExpect(jsonPath("$.concepto", is("Devolución tardía de Clean Code")))
                .andExpect(jsonPath("$.diasAtraso", is(4)))
                .andExpect(jsonPath("$.monto", is(2000))) // 4 * 500 = 2000
                .andExpect(jsonPath("$.estado", is("PENDIENTE")))
                .andExpect(jsonPath("$.fechaGeneracion", notNullValue()))
                .andExpect(jsonPath("$.fechaPago", nullValue()))
                .andExpect(jsonPath("$.metodoPago", nullValue()));
    }

    @Test
    @DisplayName("Checkpoint 3: POST /api/multas sin estudianteId retorna 400 Bad Request")
    void testGenerarMultaSinEstudianteId() throws Exception {
        String json = """
                {
                    "estudianteId": "",
                    "concepto": "Entrega tardía de libro",
                    "diasAtraso": 3
                }
                """;

        mockMvc.perform(post("/api/multas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estudianteId", notNullValue()));
    }

    @Test
    @DisplayName("Checkpoint 4: Cuarta multa pendiente para el mismo estudiante retorna 409 Conflict")
    void testLimiteMultasPendientes() throws Exception {
        // Crear 3 multas pendientes
        for (int i = 1; i <= 3; i++) {
            String json = """
                    {
                        "estudianteId": "EST-202",
                        "concepto": "Libro %d",
                        "diasAtraso": 2
                    }
                    """.formatted(i);

            mockMvc.perform(post("/api/multas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated());
        }

        // Intento de generar la 4ta multa
        String cuartaMultaJson = """
                {
                    "estudianteId": "EST-202",
                    "concepto": "Libro 4 acumulado",
                    "diasAtraso": 1
                }
                """;

        mockMvc.perform(post("/api/multas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuartaMultaJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("ya tiene 3 multas pendientes (límite: 3)")));
    }

    @Test
    @DisplayName("Checkpoint 5: GET /api/multas/{id} con ID inexistente retorna 404 Not Found")
    void testBuscarMultaInexistente() throws Exception {
        mockMvc.perform(get("/api/multas/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("Multa 9999 no encontrada")));
    }

    @Test
    @DisplayName("Checkpoint 6: PATCH /api/multas/{id}/pagar marca como PAGADA y segundo intento retorna 409 Conflict")
    void testPagarEnVentanillaYReintento() throws Exception {
        // Crear multa
        Multa multa = new Multa("EST-303", "Cálculo Integral", 10);
        multa = multaRepository.save(multa);
        Long id = multa.getId();

        // Primer pago en ventanilla -> 200 OK
        mockMvc.perform(patch("/api/multas/" + id + "/pagar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("PAGADA")))
                .andExpect(jsonPath("$.metodoPago", is("VENTANILLA")))
                .andExpect(jsonPath("$.fechaPago", notNullValue()));

        // Segundo pago en ventanilla sobre multa ya pagada -> 409 Conflict
        mockMvc.perform(patch("/api/multas/" + id + "/pagar"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("ya fue pagada")));
    }

    @Test
    @DisplayName("Listar por estudiante: GET /api/multas/estudiante/{estudianteId}")
    void testListarPorEstudiante() throws Exception {
        Multa m1 = new Multa("EST-404", "Física II", 2);
        Multa m2 = new Multa("EST-404", "Química General", 3);
        Multa m3 = new Multa("EST-505", "Álgebra Lineal", 1);
        multaRepository.save(m1);
        multaRepository.save(m2);
        multaRepository.save(m3);

        mockMvc.perform(get("/api/multas/estudiante/EST-404"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].estudianteId", is("EST-404")))
                .andExpect(jsonPath("$[1].estudianteId", is("EST-404")));
    }
}
