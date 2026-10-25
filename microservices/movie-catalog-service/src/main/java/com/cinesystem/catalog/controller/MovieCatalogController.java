package com.cinesystem.catalog.controller;

import com.cinesystem.catalog.model.Movie;
import com.cinesystem.catalog.model.SeatReservationRequest;
import com.cinesystem.catalog.model.SeatReservationResponse;
import com.cinesystem.catalog.service.MovieCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador REST del Catálogo de Películas.
 * Expone 4 endpoints de negocio + health check propio.
 */
@RestController
@RequestMapping("/api/movies")
public class MovieCatalogController {

    private static final Logger log = LoggerFactory.getLogger(MovieCatalogController.class);

    private final MovieCatalogService catalogService;

    public MovieCatalogController(MovieCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * Endpoint 1: Listar toda la cartelera actual.
     * GET /api/movies
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllMovies() {
        log.info("GET /api/movies - Consultando cartelera completa");
        List<Movie> movies = catalogService.getAllMovies();
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "count", movies.size(),
                "movies", movies
        ));
    }

    /**
     * Endpoint 2: Consultar disponibilidad de asientos para una película.
     * GET /api/movies/{id}/availability
     */
    @GetMapping("/{id}/availability")
    public ResponseEntity<Map<String, Object>> getMovieAvailability(@PathVariable String id) {
        log.info("GET /api/movies/{}/availability - Consultando disponibilidad", id);

        Optional<Movie> movieOpt = catalogService.getMovieById(id);

        if (movieOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Película no encontrada: " + id));
        }

        Movie movie = movieOpt.get();
        List<Map<String, Object>> showtimeData = movie.getShowtimes().stream()
                .map(s -> Map.<String, Object>of(
                        "showtimeId", s.getShowtimeId(),
                        "hall", s.getHallName(),
                        "time", s.getTime(),
                        "totalSeats", s.getTotalSeats(),
                        "availableSeats", s.getAvailableSeats(),
                        "occupancyPercent", Math.round((1.0 - (double) s.getAvailableSeats() / s.getTotalSeats()) * 100)
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "movieId", movie.getId(),
                "title", movie.getTitle(),
                "showtimes", showtimeData
        ));
    }

    /**
     * Endpoint 3: Reservar asientos en una función específica.
     * POST /api/movies/{id}/reserve-seats
     */
    @PostMapping("/{id}/reserve-seats")
    public ResponseEntity<SeatReservationResponse> reserveSeats(
            @PathVariable String id,
            @RequestBody SeatReservationRequest request) {

        log.info("POST /api/movies/{}/reserve-seats - Reservando {} asientos para función {}",
                id, request.getQuantity(), request.getShowtimeId());

        SeatReservationResponse response = catalogService.reserveSeats(id, request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            // 409 Conflict si no hay disponibilidad
            if (response.getMessage().startsWith("Sin disponibilidad")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    /**
     * Endpoint 4: Liberar asientos (llamado por ticketing-service al cancelar).
     * POST /api/movies/{id}/release-seats
     */
    @PostMapping("/{id}/release-seats")
    public ResponseEntity<Map<String, Object>> releaseSeats(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {

        String showtimeId = (String) body.get("showtimeId");
        int quantity = (Integer) body.get("quantity");

        log.info("POST /api/movies/{}/release-seats - Liberando {} asientos en función {}",
                id, quantity, showtimeId);

        boolean released = catalogService.releaseSeats(id, showtimeId, quantity);

        if (released) {
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Asientos liberados correctamente",
                    "releasedQuantity", quantity
            ));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", "Película o función no encontrada"));
        }
    }
}
