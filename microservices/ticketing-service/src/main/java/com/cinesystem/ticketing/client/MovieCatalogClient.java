package com.cinesystem.ticketing.client;

import com.cinesystem.ticketing.model.PurchaseRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Cliente HTTP para comunicarse con movie-catalog-service.
 * Usa Spring WebClient con timeout configurado.
 * Si el Servicio A no responde en el timeout, captura el error y retorna un fallback.
 */
@Component
public class MovieCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(MovieCatalogClient.class);

    private final WebClient webClient;
    private final long timeoutMs;

    public MovieCatalogClient(
            @Value("${catalog.service.url:http://movie-catalog-service:8081}") String catalogUrl,
            @Value("${catalog.service.timeout-ms:2000}") long timeoutMs,
            WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(catalogUrl)
                .build();
        this.timeoutMs = timeoutMs;
        log.info("MovieCatalogClient configurado: URL={}, Timeout={}ms", catalogUrl, timeoutMs);
    }

    /**
     * Verifica disponibilidad de asientos en una función.
     * Retorna el cuerpo de la respuesta o null si hay timeout/error.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> checkAvailability(String movieId, String showtimeId) {
        try {
            return webClient.get()
                    .uri("/api/movies/{movieId}/availability", movieId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> {
                        log.error("Error al consultar disponibilidad: HTTP {}", response.statusCode());
                        return Mono.error(new RuntimeException("Error HTTP: " + response.statusCode()));
                    })
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .doOnError(e -> log.error("Timeout/Error consultando catálogo: {}", e.getMessage()))
                    .block();
        } catch (Exception e) {
            log.error("Fallo en consulta de disponibilidad (movieId={}, showtimeId={}): {}",
                    movieId, showtimeId, e.getMessage());
            return null; // Fallback: retorna null para que el servicio maneje el error
        }
    }

    /**
     * Reserva asientos en movie-catalog-service.
     * Con timeout configurado — si supera el límite, retorna null como fallback.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> reserveSeats(String movieId, PurchaseRequest request) {
        Map<String, Object> body = Map.of(
                "showtimeId", request.getShowtimeId(),
                "quantity", request.getQuantity(),
                "customerName", request.getCustomerName()
        );

        try {
            return webClient.post()
                    .uri("/api/movies/{movieId}/reserve-seats", movieId)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(status -> status.value() == 409, response -> {
                        // 409 Conflict = sin disponibilidad, NO es un error de conexión
                        log.warn("Sin disponibilidad para movieId={}, showtimeId={}", movieId, request.getShowtimeId());
                        return Mono.error(new SeatUnavailableException("Sin disponibilidad de asientos"));
                    })
                    .onStatus(HttpStatusCode::isError, response -> {
                        log.error("Error HTTP al reservar asientos: {}", response.statusCode());
                        return Mono.error(new RuntimeException("Error HTTP reservando: " + response.statusCode()));
                    })
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .doOnError(e -> {
                        if (!(e instanceof SeatUnavailableException)) {
                            log.error("Timeout/Error reservando asientos: {}", e.getMessage());
                        }
                    })
                    .block();
        } catch (SeatUnavailableException e) {
            // Propagar como tipo especial para manejo diferenciado
            throw e;
        } catch (Exception e) {
            log.error("Fallo crítico reservando asientos (movieId={}): {}", movieId, e.getMessage());
            return null; // Fallback: null indica timeout o error de conexión
        }
    }

    /**
     * Libera asientos (para cancelaciones).
     */
    @SuppressWarnings("unchecked")
    public boolean releaseSeats(String movieId, String showtimeId, int quantity) {
        Map<String, Object> body = Map.of(
                "showtimeId", showtimeId,
                "quantity", quantity
        );

        try {
            Map<String, Object> response = webClient.post()
                    .uri("/api/movies/{movieId}/release-seats", movieId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();
            return response != null && "success".equals(response.get("status"));
        } catch (Exception e) {
            log.error("Error liberando asientos (movieId={}, showtimeId={}): {}", movieId, showtimeId, e.getMessage());
            return false;
        }
    }

    /**
     * Excepción específica para caso de asientos sin disponibilidad (409 Conflict).
     */
    public static class SeatUnavailableException extends RuntimeException {
        public SeatUnavailableException(String message) {
            super(message);
        }
    }
}
