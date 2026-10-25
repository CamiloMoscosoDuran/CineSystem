package com.cinesystem.catalog.controller;

import com.cinesystem.catalog.service.MovieCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador de administración del catálogo.
 * Permite configurar el delay artificial en tiempo de ejecución (Casuística 2).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final MovieCatalogService catalogService;

    public AdminController(MovieCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * Endpoint de administración: Configurar delay artificial.
     * POST /api/admin/delay
     * Body: { "delayMs": 3000 }
     *
     * Usado en Casuística 2 para simular lentitud del servicio.
     */
    @PostMapping("/delay")
    public ResponseEntity<Map<String, Object>> setDelay(@RequestBody Map<String, Long> body) {
        Long delayMs = body.get("delayMs");

        if (delayMs == null || delayMs < 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "delayMs debe ser >= 0"));
        }

        long previousDelay = catalogService.getArtificialDelayMs();
        catalogService.setArtificialDelay(delayMs);

        log.warn("ADMIN: Delay artificial cambiado de {}ms a {}ms", previousDelay, delayMs);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Delay artificial configurado",
                "previousDelayMs", previousDelay,
                "newDelayMs", delayMs,
                "warning", delayMs > 0 ? "El servicio responderá lentamente" : "Delay desactivado"
        ));
    }

    /**
     * Consulta el delay artificial actual.
     * GET /api/admin/delay
     */
    @GetMapping("/delay")
    public ResponseEntity<Map<String, Object>> getDelay() {
        long currentDelay = catalogService.getArtificialDelayMs();
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "currentDelayMs", currentDelay,
                "isDelayActive", currentDelay > 0
        ));
    }

    /**
     * Health check propio del servicio (rápido, sin dependencias externas).
     * GET /api/admin/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "service", "movie-catalog-service",
                "status", "UP",
                "artificialDelayMs", catalogService.getArtificialDelayMs()
        ));
    }
}
