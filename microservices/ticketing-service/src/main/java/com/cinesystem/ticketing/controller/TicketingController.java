package com.cinesystem.ticketing.controller;

import com.cinesystem.ticketing.model.CancelRequest;
import com.cinesystem.ticketing.model.PurchaseRequest;
import com.cinesystem.ticketing.model.Ticket;
import com.cinesystem.ticketing.service.TicketingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador REST de la Boletería.
 * Expone 4 endpoints de negocio + un health check propio.
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketingController {

    private static final Logger log = LoggerFactory.getLogger(TicketingController.class);

    private final TicketingService ticketingService;

    public TicketingController(TicketingService ticketingService) {
        this.ticketingService = ticketingService;
    }

    /**
     * Endpoint 1: Procesar compra de boletos.
     * Invoca internamente al movie-catalog-service para reservar asientos.
     * POST /api/tickets/purchase
     */
    @PostMapping("/purchase")
    public ResponseEntity<Map<String, Object>> purchaseTicket(@RequestBody PurchaseRequest request) {
        log.info("POST /api/tickets/purchase - Cliente: {}, Película: {}, Cantidad: {}",
                request.getCustomerName(), request.getMovieId(), request.getQuantity());

        if (request.getMovieId() == null || request.getShowtimeId() == null
                || request.getCustomerName() == null || request.getQuantity() <= 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Campos requeridos: movieId, showtimeId, customerName, quantity (> 0)"
            ));
        }

        Map<String, Object> result = ticketingService.purchaseTicket(request);
        String status = (String) result.get("status");

        return switch (status) {
            case "success" -> ResponseEntity.ok(result);
            case "failed" -> {
                String reason = (String) result.get("reason");
                yield switch (reason) {
                    case "NO_AVAILABILITY" -> ResponseEntity.status(HttpStatus.CONFLICT).body(result);
                    case "CATALOG_SERVICE_UNAVAILABLE" -> ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(result);
                    default -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
                };
            }
            default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        };
    }

    /**
     * Endpoint 2: Consultar boleto emitido por ID.
     * GET /api/tickets/{ticketId}
     */
    @GetMapping("/{ticketId}")
    public ResponseEntity<Map<String, Object>> getTicket(@PathVariable String ticketId) {
        log.info("GET /api/tickets/{} - Consultando boleto", ticketId);

        Optional<Ticket> ticketOpt = ticketingService.getTicketById(ticketId);

        if (ticketOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Boleto no encontrado: " + ticketId));
        }

        Ticket ticket = ticketOpt.get();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", "success");
        details.put("ticketId", ticket.getTicketId());
        details.put("movieId", ticket.getMovieId());
        details.put("movieTitle", ticket.getMovieTitle());
        details.put("showtimeId", ticket.getShowtimeId());
        details.put("hall", ticket.getHallName());
        details.put("showtime", ticket.getShowtime());
        details.put("customerName", ticket.getCustomerName());
        details.put("customerEmail", ticket.getCustomerEmail());
        details.put("quantity", ticket.getQuantity());
        details.put("totalPrice", ticket.getTotalPrice());
        details.put("purchaseTime", ticket.getPurchaseTime().toString());
        details.put("status", ticket.getStatus().name());
        return ResponseEntity.ok(details);
    }

    /**
     * Endpoint 3: Resumen de ventas del día.
     * GET /api/tickets/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getDailySummary() {
        log.info("GET /api/tickets/summary - Consultando resumen del día");
        return ResponseEntity.ok(ticketingService.getDailySummary());
    }

    /**
     * Endpoint 4: Cancelar boleto y liberar asientos.
     * POST /api/tickets/cancel
     */
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancelTicket(@RequestBody CancelRequest request) {
        log.info("POST /api/tickets/cancel - ticketId: {}", request.getTicketId());

        if (request.getTicketId() == null || request.getTicketId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", "ticketId es requerido"));
        }

        Map<String, Object> result = ticketingService.cancelTicket(request);
        String status = (String) result.get("status");

        if ("success".equals(status)) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
    }
}
