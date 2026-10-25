package com.cinesystem.catalog.controller;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador que expone las métricas de Prometheus en la ruta /metrics.
 * El enunciado requiere que el endpoint responda exactamente en /metrics.
 */
@RestController
public class MetricsController {

    private final PrometheusMeterRegistry prometheusMeterRegistry;

    public MetricsController(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
    }

    /**
     * Endpoint de métricas en /metrics para que Prometheus pueda raspar.
     * GET /metrics
     */
    @GetMapping(value = "/metrics", produces = "text/plain; version=0.0.4; charset=utf-8")
    public ResponseEntity<String> metrics() {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/plain; version=0.0.4; charset=utf-8"))
                .body(prometheusMeterRegistry.scrape());
    }
}
