package com.cinesystem.ticketing.controller;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controlador que expone métricas en /metrics para Prometheus.
 */
@RestController
public class MetricsController {

    private final PrometheusMeterRegistry prometheusMeterRegistry;

    public MetricsController(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
    }

    /**
     * Endpoint de métricas en /metrics para Prometheus.
     * GET /metrics
     */
    @GetMapping(value = "/metrics", produces = "text/plain; version=0.0.4; charset=utf-8")
    public ResponseEntity<String> metrics() {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("text/plain; version=0.0.4; charset=utf-8"))
                .body(prometheusMeterRegistry.scrape());
    }

    /**
     * Health check propio del servicio.
     * GET /health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "service", "ticketing-service",
                "status", "UP"
        ));
    }
}
