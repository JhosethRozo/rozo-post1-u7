package com.example.multas.controller;

import com.example.multas.model.Multa;
import com.example.multas.service.MultaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/multas")
public class MultaController {

    private final MultaService multaService;

    public MultaController(MultaService multaService) {
        this.multaService = multaService;
    }

    @GetMapping
    public List<Multa> listar() {
        return multaService.listarTodas();
    }

    @GetMapping("/{id}")
    public Multa buscar(@PathVariable Long id) {
        return multaService.buscarPorId(id);
    }

    @GetMapping("/estudiante/{estudianteId}")
    public List<Multa> listarPorEstudiante(@PathVariable String estudianteId) {
        return multaService.listarPorEstudiante(estudianteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Multa generar(@Valid @RequestBody GenerarMultaRequest request) {
        return multaService.generar(request.estudianteId(), request.concepto(), request.diasAtraso());
    }

    @PatchMapping("/{id}/pagar")
    public Multa pagarEnVentanilla(@PathVariable Long id) {
        return multaService.pagarEnVentanilla(id);
    }

    // Parte 2: Pago en línea a través del puerto de dominio
    @PostMapping("/{id}/pagar-en-linea")
    public Multa pagarEnLinea(@PathVariable Long id) {
        return multaService.pagarConPasarela(id);
    }
}
