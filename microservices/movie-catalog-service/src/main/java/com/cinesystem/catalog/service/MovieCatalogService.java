package com.cinesystem.catalog.service;

import com.cinesystem.catalog.model.Movie;
import com.cinesystem.catalog.model.Movie.Showtime;
import com.cinesystem.catalog.model.SeatReservationRequest;
import com.cinesystem.catalog.model.SeatReservationResponse;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Servicio principal del catálogo de películas.
 * Almacenamiento en memoria usando ConcurrentHashMap para thread-safety.
 * Registra métricas Micrometer: Counter, Gauge y Timer.
 */
@Service
public class MovieCatalogService {

    private static final Logger log = LoggerFactory.getLogger(MovieCatalogService.class);

    // ==============================
    // ALMACENAMIENTO EN MEMORIA
    // ==============================
    private final ConcurrentHashMap<String, Movie> moviesStore = new ConcurrentHashMap<>();

    // ==============================
    // DELAY ARTIFICIAL (Casuística 2)
    // ==============================
    @Value("${ARTIFICIAL_DELAY_MS:0}")
    private long artificialDelayMs;

    // ==============================
    // MICROMETER - MÉTRICAS
    // ==============================
    private final MeterRegistry meterRegistry;

    // Counter: Total de consultas al catálogo
    private Counter moviesRequestedCounter;

    // Counter: Total de reservas exitosas
    private Counter reservationsSuccessCounter;

    // Counter: Total de reservas fallidas (sin asientos)
    private Counter reservationsFailedCounter;

    // Timer: Tiempo de respuesta de endpoints del catálogo
    private Timer catalogResponseTimer;

    // Gauge backed por AtomicLong: delay artificial actual en ms
    private final AtomicLong currentDelayMs = new AtomicLong(0);

    // Gauge backed por AtomicLong: total de asientos disponibles globalmente
    private final AtomicLong totalAvailableSeatsGlobal = new AtomicLong(0);

    public MovieCatalogService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        // Sincronizar delay con variable de entorno
        currentDelayMs.set(artificialDelayMs);

        // Registrar métricas Micrometer
        // 1. Counter - total de consultas al catálogo de películas
        moviesRequestedCounter = Counter.builder("catalog_movies_requested_total")
                .description("Total de veces que se consultó el catálogo de películas")
                .tag("service", "movie-catalog-service")
                .register(meterRegistry);

        // 2. Counter - reservas de asientos exitosas
        reservationsSuccessCounter = Counter.builder("catalog_seat_reservations_success_total")
                .description("Total de reservas de asientos completadas exitosamente")
                .tag("service", "movie-catalog-service")
                .register(meterRegistry);

        // 3. Counter - reservas fallidas por falta de asientos
        reservationsFailedCounter = Counter.builder("catalog_seat_reservations_failed_total")
                .description("Total de reservas de asientos fallidas (sin disponibilidad)")
                .tag("service", "movie-catalog-service")
                .register(meterRegistry);

        // 4. Gauge - delay artificial configurado (para Casuística 2, visible en Grafana)
        Gauge.builder("catalog_artificial_delay_milliseconds", currentDelayMs, AtomicLong::get)
                .description("Delay artificial configurado en el servicio de catálogo (ms)")
                .tag("service", "movie-catalog-service")
                .register(meterRegistry);

        // 5. Gauge - total de asientos disponibles en toda la cartelera
        Gauge.builder("catalog_available_seats_total", totalAvailableSeatsGlobal, AtomicLong::get)
                .description("Total de asientos disponibles en toda la cartelera activa")
                .tag("service", "movie-catalog-service")
                .register(meterRegistry);

        // 6. Timer - tiempo de respuesta del catálogo
        catalogResponseTimer = Timer.builder("catalog_response_time_seconds")
                .description("Tiempo de respuesta de los endpoints del catálogo")
                .tag("service", "movie-catalog-service")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        // Inicializar datos de ejemplo en memoria
        initializeSampleData();

        log.info("MovieCatalogService inicializado. Delay artificial: {}ms. Películas cargadas: {}",
                artificialDelayMs, moviesStore.size());
    }

    /**
     * Carga datos de ejemplo en memoria al inicio.
     */
    private void initializeSampleData() {
        // Película 1: Avengers
        Movie avengers = new Movie("MOV001", "Avengers: Infinity War", "Acción/Sci-Fi", 149, "Anthony Russo");
        avengers.getShowtimes().add(new Showtime("SH001", "Sala IMAX 1", "14:00", 200));
        avengers.getShowtimes().add(new Showtime("SH002", "Sala 2", "17:30", 150));
        avengers.getShowtimes().add(new Showtime("SH003", "Sala VIP", "20:00", 50));
        moviesStore.put(avengers.getId(), avengers);

        // Película 2: Dune
        Movie dune = new Movie("MOV002", "Dune: Part Two", "Ciencia Ficción", 166, "Denis Villeneuve");
        dune.getShowtimes().add(new Showtime("SH004", "Sala IMAX 1", "15:00", 200));
        dune.getShowtimes().add(new Showtime("SH005", "Sala 3", "19:00", 120));
        moviesStore.put(dune.getId(), dune);

        // Película 3: Barbie
        Movie barbie = new Movie("MOV003", "Barbie", "Comedia/Fantasía", 114, "Greta Gerwig");
        barbie.getShowtimes().add(new Showtime("SH006", "Sala 4", "13:00", 180));
        barbie.getShowtimes().add(new Showtime("SH007", "Sala 4", "16:00", 180));
        moviesStore.put(barbie.getId(), barbie);

        // Película 4: Oppenheimer
        Movie oppenheimer = new Movie("MOV004", "Oppenheimer", "Drama/Historia", 180, "Christopher Nolan");
        oppenheimer.getShowtimes().add(new Showtime("SH008", "Sala IMAX 2", "18:00", 250));
        moviesStore.put(oppenheimer.getId(), oppenheimer);

        // Actualizar gauge de asientos totales
        refreshTotalAvailableSeats();
    }

    /**
     * Recalcula el total de asientos disponibles globalmente y actualiza el Gauge.
     */
    public void refreshTotalAvailableSeats() {
        long total = moviesStore.values().stream()
                .flatMap(m -> m.getShowtimes().stream())
                .mapToLong(Showtime::getAvailableSeats)
                .sum();
        totalAvailableSeatsGlobal.set(total);
    }

    // ==============================
    // MÉTODOS DE NEGOCIO
    // ==============================

    /**
     * Retorna todas las películas en cartelera.
     * Aplica el delay artificial si está configurado.
     */
    public List<Movie> getAllMovies() {
        return catalogResponseTimer.record(() -> {
            applyArtificialDelay();
            moviesRequestedCounter.increment();
            return new ArrayList<>(moviesStore.values());
        });
    }

    /**
     * Busca una película por ID y retorna su disponibilidad de asientos.
     */
    public Optional<Movie> getMovieById(String movieId) {
        return catalogResponseTimer.record(() -> {
            applyArtificialDelay();
            moviesRequestedCounter.increment();
            return Optional.ofNullable(moviesStore.get(movieId));
        });
    }

    /**
     * Reserva asientos para una función específica.
     * Operación atómica usando compareAndSet.
     */
    public SeatReservationResponse reserveSeats(String movieId, SeatReservationRequest request) {
        applyArtificialDelay();

        Movie movie = moviesStore.get(movieId);
        if (movie == null) {
            reservationsFailedCounter.increment();
            return new SeatReservationResponse(false,
                    "Película no encontrada: " + movieId, request.getShowtimeId(), 0, 0);
        }

        Optional<Showtime> showtimeOpt = movie.getShowtimes().stream()
                .filter(s -> s.getShowtimeId().equals(request.getShowtimeId()))
                .findFirst();

        if (showtimeOpt.isEmpty()) {
            reservationsFailedCounter.increment();
            return new SeatReservationResponse(false,
                    "Función no encontrada: " + request.getShowtimeId(), request.getShowtimeId(), 0, 0);
        }

        Showtime showtime = showtimeOpt.get();
        boolean reserved = showtime.reserveSeats(request.getQuantity());

        if (reserved) {
            reservationsSuccessCounter.increment();
            refreshTotalAvailableSeats();
            log.info("Reserva exitosa: {} asientos en función {} para {}",
                    request.getQuantity(), request.getShowtimeId(), request.getCustomerName());
            return new SeatReservationResponse(true,
                    "Reserva confirmada",
                    request.getShowtimeId(),
                    request.getQuantity(),
                    showtime.getAvailableSeats());
        } else {
            reservationsFailedCounter.increment();
            log.warn("Sin disponibilidad: {} asientos solicitados, {} disponibles en {}",
                    request.getQuantity(), showtime.getAvailableSeats(), request.getShowtimeId());
            return new SeatReservationResponse(false,
                    "Sin disponibilidad. Asientos disponibles: " + showtime.getAvailableSeats(),
                    request.getShowtimeId(),
                    0,
                    showtime.getAvailableSeats());
        }
    }

    /**
     * Libera asientos (para cancelaciones desde ticketing-service).
     */
    public boolean releaseSeats(String movieId, String showtimeId, int quantity) {
        Movie movie = moviesStore.get(movieId);
        if (movie == null) return false;

        Optional<Showtime> showtimeOpt = movie.getShowtimes().stream()
                .filter(s -> s.getShowtimeId().equals(showtimeId))
                .findFirst();

        if (showtimeOpt.isEmpty()) return false;

        showtimeOpt.get().releaseSeats(quantity);
        refreshTotalAvailableSeats();
        log.info("Liberados {} asientos en función {}", quantity, showtimeId);
        return true;
    }

    /**
     * Configura el delay artificial en tiempo de ejecución (Casuística 2).
     */
    public void setArtificialDelay(long delayMs) {
        log.warn("CASUÍSTICA 2: Configurando delay artificial a {}ms", delayMs);
        currentDelayMs.set(delayMs);
        artificialDelayMs = delayMs;
    }

    public long getArtificialDelayMs() {
        return currentDelayMs.get();
    }

    /**
     * Aplica el delay artificial si está configurado.
     */
    private void applyArtificialDelay() {
        long delay = currentDelayMs.get();
        if (delay > 0) {
            try {
                log.debug("Aplicando delay artificial de {}ms", delay);
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Delay artificial interrumpido");
            }
        }
    }
}
