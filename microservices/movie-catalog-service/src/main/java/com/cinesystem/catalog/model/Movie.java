package com.cinesystem.catalog.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modelo de datos para una Película en cartelera.
 * Almacenamiento en memoria usando tipos thread-safe.
 */
public class Movie {

    private final String id;
    private final String title;
    private final String genre;
    private final int durationMinutes;
    private final String director;
    private final List<Showtime> showtimes;

    public Movie(String id, String title, String genre, int durationMinutes, String director) {
        this.id = id;
        this.title = title;
        this.genre = genre;
        this.durationMinutes = durationMinutes;
        this.director = director;
        this.showtimes = new CopyOnWriteArrayList<>();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getGenre() { return genre; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getDirector() { return director; }
    public List<Showtime> getShowtimes() { return showtimes; }

    /**
     * Función/sesión de una película: sala, horario y asientos disponibles.
     */
    public static class Showtime {
        private final String showtimeId;
        private final String hallName;
        private final String time;
        private final int totalSeats;
        private final AtomicInteger availableSeats;

        public Showtime(String showtimeId, String hallName, String time, int totalSeats) {
            this.showtimeId = showtimeId;
            this.hallName = hallName;
            this.time = time;
            this.totalSeats = totalSeats;
            this.availableSeats = new AtomicInteger(totalSeats);
        }

        public String getShowtimeId() { return showtimeId; }
        public String getHallName() { return hallName; }
        public String getTime() { return time; }
        public int getTotalSeats() { return totalSeats; }
        public int getAvailableSeats() { return availableSeats.get(); }

        /**
         * Reserva asientos de forma atómica. Retorna true si fue exitoso.
         */
        public boolean reserveSeats(int quantity) {
            while (true) {
                int current = availableSeats.get();
                if (current < quantity) {
                    return false;
                }
                if (availableSeats.compareAndSet(current, current - quantity)) {
                    return true;
                }
            }
        }

        /**
         * Libera asientos (para cancelaciones).
         */
        public void releaseSeats(int quantity) {
            int released = availableSeats.addAndGet(quantity);
            // No exceder el total original
            if (released > totalSeats) {
                availableSeats.set(totalSeats);
            }
        }
    }
}
