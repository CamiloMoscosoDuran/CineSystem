package com.cinesystem.ticketing.service;

import com.cinesystem.ticketing.client.MovieCatalogClient;
import com.cinesystem.ticketing.client.MovieCatalogClient.SeatUnavailableException;
import com.cinesystem.ticketing.model.CancelRequest;
import com.cinesystem.ticketing.model.PurchaseRequest;
import com.cinesystem.ticketing.model.Ticket;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Servicio principal de boletería.
 * Almacenamiento en memoria con ConcurrentHashMap.
 * Registra métricas Micrometer: Counter, Gauge y Timer.
 * Se comunica con movie-catalog-service via WebClient.
 */
@Service
public class TicketingService {

    private static final Logger log = LoggerFactory.getLogger(TicketingService.class);

    // ==============================
    // ALMACENAMIENTO EN MEMORIA
    // ==============================
    private final ConcurrentHashMap<String, Ticket> ticketsStore = new ConcurrentHashMap<>();

    // ==============================
    // DEPENDENCIAS
    // ==============================
    private final MovieCatalogClient catalogClient;
    private final MeterRegistry meterRegistry;

    // ==============================
    // MÉTRICAS MICROMETER
    // ==============================
    // Counter: Total de boletos vendidos exitosamente
    private Counter ticketsSoldCounter;

    // Counter: Total de compras fallidas (timeout, sin asientos, error)
    private Counter purchaseFailuresCounter;

    // Counter: Total de cancelaciones
    private Counter cancellationsCounter;

    // Gauge: Reservas activas en este momento (boletos CONFIRMED)
    private final AtomicInteger activeReservationsGauge = new AtomicInteger(0);

    // Gauge: Ingresos acumulados del día
    private final AtomicLong dailyRevenueGauge = new AtomicLong(0);

    // Timer: Tiempo total del proceso de checkout (incluye llamada a Servicio A)
    private Timer checkoutDurationTimer;

    public TicketingService(MovieCatalogClient catalogClient, MeterRegistry meterRegistry) {
        this.catalogClient = catalogClient;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        // 1. Counter - boletos vendidos
        ticketsSoldCounter = Counter.builder("ticketing_tickets_sold_total")
                .description("Total de boletos vendidos exitosamente")
                .tag("service", "ticketing-service")
                .register(meterRegistry);

        // 2. Counter - fallos en proceso de compra
        purchaseFailuresCounter = Counter.builder("ticketing_purchase_failures_total")
                .description("Total de intentos de compra fallidos")
                .tag("service", "ticketing-service")
                .register(meterRegistry);

        // 3. Counter - cancelaciones
        cancellationsCounter = Counter.builder("ticketing_cancellations_total")
                .description("Total de boletos cancelados")
                .tag("service", "ticketing-service")
                .register(meterRegistry);

        // 4. Gauge - reservas activas en este momento
        Gauge.builder("ticketing_active_reservations_gauge", activeReservationsGauge, AtomicInteger::get)
                .description("Cantidad de reservas/boletos activos (confirmados) en el sistema")
                .tag("service", "ticketing-service")
                .register(meterRegistry);

        // 5. Gauge - ingresos del día (en centavos para usar long)
        Gauge.builder("ticketing_daily_revenue_cents", dailyRevenueGauge, AtomicLong::get)
                .description("Ingresos acumulados del día en centavos")
                .tag("service", "ticketing-service")
                .register(meterRegistry);

        // 6. Timer - duración del proceso de checkout completo
        checkoutDurationTimer = Timer.builder("ticketing_checkout_duration_seconds")
                .description("Tiempo total del proceso de compra de boleto (incluye consulta al catálogo)")
                .tag("service", "ticketing-service")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        log.info("TicketingService inicializado con métricas Micrometer registradas.");
    }

    // ==============================
    // MÉTODOS DE NEGOCIO
    // ==============================

    /**
     * Procesa la compra de un boleto.
     * 1. Llama a movie-catalog-service para reservar asientos (con timeout).
     * 2. Si el Servicio A responde OK, crea el boleto y lo guarda en memoria.
     * 3. Si hay timeout o error, registra el fallo y retorna respuesta controlada.
     */
    public Map<String, Object> purchaseTicket(PurchaseRequest request) {
        return checkoutDurationTimer.record(() -> {
            log.info("Procesando compra: movieId={}, showtimeId={}, cantidad={}, cliente={}",
                    request.getMovieId(), request.getShowtimeId(),
                    request.getQuantity(), request.getCustomerName());

            // PASO 1: Llamar al Servicio A para reservar asientos
            Map<String, Object> reservationResult;
            try {
                reservationResult = catalogClient.reserveSeats(request.getMovieId(), request);
            } catch (SeatUnavailableException e) {
                // Caso: No hay asientos (409 Conflict del Servicio A)
                purchaseFailuresCounter.increment(); // reason: no_availability
                log.warn("Compra rechazada: sin disponibilidad para {}", request.getShowtimeId());
                return Map.of(
                        "status", "failed",
                        "reason", "NO_AVAILABILITY",
                        "message", "No hay asientos disponibles para esta función",
                        "movieId", request.getMovieId(),
                        "showtimeId", request.getShowtimeId()
                );
            }

            // PASO 2: Si reservationResult es null = timeout o error de conexión
            if (reservationResult == null) {
                purchaseFailuresCounter.increment(); // reason: catalog_timeout
                log.error("Compra fallida: timeout comunicando con movie-catalog-service");
                return Map.of(
                        "status", "failed",
                        "reason", "CATALOG_SERVICE_UNAVAILABLE",
                        "message", "El servicio de catálogo no respondió a tiempo. Intente nuevamente.",
                        "movieId", request.getMovieId(),
                        "retryAfterSeconds", 5
                );
            }

            // PASO 3: Reserva exitosa, crear boleto
            boolean reserved = Boolean.TRUE.equals(reservationResult.get("success"));
            if (!reserved) {
                purchaseFailuresCounter.increment(); // reason: reservation_failed
                return Map.of(
                        "status", "failed",
                        "reason", "RESERVATION_FAILED",
                        "message", reservationResult.getOrDefault("message", "Error en reserva")
                );
            }

            // PASO 4: Obtener datos de la película para el boleto
            // (extraer del resultado de reserva o usar los del request)
            double pricePerSeat = request.getPricePerSeat() > 0 ? request.getPricePerSeat() : 12.50;
            double totalPrice = pricePerSeat * request.getQuantity();

            // Obtener info de la función (si viene en la respuesta del catálogo)
            String hallName = "Sala Asignada";
            String showtime = "Horario Confirmado";

            Ticket ticket = new Ticket(
                    request.getMovieId(),
                    "Película " + request.getMovieId(), // Se podría enriquecer con más datos
                    request.getShowtimeId(),
                    hallName,
                    showtime,
                    request.getCustomerName(),
                    request.getCustomerEmail() != null ? request.getCustomerEmail() : "",
                    request.getQuantity(),
                    totalPrice
            );

            ticketsStore.put(ticket.getTicketId(), ticket);

            // Actualizar métricas
            ticketsSoldCounter.increment();
            activeReservationsGauge.incrementAndGet();
            dailyRevenueGauge.addAndGet((long)(totalPrice * 100)); // en centavos

            log.info("Compra exitosa: ticketId={}, totalPrice=${}", ticket.getTicketId(), totalPrice);

            return Map.of(
                    "status", "success",
                    "ticketId", ticket.getTicketId(),
                    "movieId", ticket.getMovieId(),
                    "showtimeId", ticket.getShowtimeId(),
                    "customerName", ticket.getCustomerName(),
                    "quantity", ticket.getQuantity(),
                    "totalPrice", ticket.getTotalPrice(),
                    "purchaseTime", ticket.getPurchaseTime().toString(),
                    "remainingSeats", reservationResult.getOrDefault("remainingSeats", "N/A")
            );
        });
    }

    /**
     * Consulta un boleto por ID.
     */
    public Optional<Ticket> getTicketById(String ticketId) {
        return Optional.ofNullable(ticketsStore.get(ticketId));
    }

    /**
     * Resumen de ventas del día.
     */
    public Map<String, Object> getDailySummary() {
        List<Ticket> confirmedTickets = ticketsStore.values().stream()
                .filter(t -> t.getStatus() == Ticket.Status.CONFIRMED)
                .collect(Collectors.toList());

        double totalRevenue = confirmedTickets.stream()
                .mapToDouble(Ticket::getTotalPrice)
                .sum();

        int totalSeatsSold = confirmedTickets.stream()
                .mapToInt(Ticket::getQuantity)
                .sum();

        Map<String, Long> ticketsByMovie = confirmedTickets.stream()
                .collect(Collectors.groupingBy(Ticket::getMovieId, Collectors.counting()));

        return Map.of(
                "date", LocalDate.now().toString(),
                "confirmedTickets", confirmedTickets.size(),
                "totalSeatsSold", totalSeatsSold,
                "totalRevenue", String.format("$%.2f", totalRevenue),
                "ticketsByMovie", ticketsByMovie,
                "cancelledTickets", ticketsStore.values().stream()
                        .filter(t -> t.getStatus() == Ticket.Status.CANCELLED)
                        .count()
        );
    }

    /**
     * Cancela un boleto y libera los asientos en movie-catalog-service.
     */
    public Map<String, Object> cancelTicket(CancelRequest request) {
        Ticket ticket = ticketsStore.get(request.getTicketId());

        if (ticket == null) {
            return Map.of("status", "error", "message", "Boleto no encontrado: " + request.getTicketId());
        }

        if (ticket.getStatus() == Ticket.Status.CANCELLED) {
            return Map.of("status", "error", "message", "El boleto ya fue cancelado previamente");
        }

        // Intentar liberar asientos en el Servicio A
        boolean seatsReleased = catalogClient.releaseSeats(
                ticket.getMovieId(), ticket.getShowtimeId(), ticket.getQuantity());

        // Cancelar el boleto independientemente del resultado de liberación de asientos
        ticket.setStatus(Ticket.Status.CANCELLED);
        cancellationsCounter.increment();
        activeReservationsGauge.decrementAndGet();

        log.info("Boleto cancelado: ticketId={}, asientos liberados={}", ticket.getTicketId(), seatsReleased);

        return Map.of(
                "status", "success",
                "message", "Boleto cancelado correctamente",
                "ticketId", ticket.getTicketId(),
                "refundAmount", ticket.getTotalPrice(),
                "seatsReleased", seatsReleased,
                "reason", request.getReason() != null ? request.getReason() : "No especificado"
        );
    }

    /**
     * Retorna todos los boletos (para debugging/admin).
     */
    public Collection<Ticket> getAllTickets() {
        return ticketsStore.values();
    }
}
