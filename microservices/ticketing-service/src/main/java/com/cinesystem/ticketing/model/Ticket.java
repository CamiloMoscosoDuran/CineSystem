package com.cinesystem.ticketing.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Modelo de un Boleto de cine.
 * Almacenamiento en memoria (sin base de datos).
 */
public class Ticket {

    public enum Status {
        CONFIRMED, CANCELLED, PENDING
    }

    private final String ticketId;
    private final String movieId;
    private final String movieTitle;
    private final String showtimeId;
    private final String hallName;
    private final String showtime;
    private final String customerName;
    private final String customerEmail;
    private final int quantity;
    private final double totalPrice;
    private final LocalDateTime purchaseTime;
    private volatile Status status;

    public Ticket(String movieId, String movieTitle, String showtimeId,
                  String hallName, String showtime, String customerName,
                  String customerEmail, int quantity, double totalPrice) {
        this.ticketId = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.movieId = movieId;
        this.movieTitle = movieTitle;
        this.showtimeId = showtimeId;
        this.hallName = hallName;
        this.showtime = showtime;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.purchaseTime = LocalDateTime.now();
        this.status = Status.CONFIRMED;
    }

    // Getters
    public String getTicketId() { return ticketId; }
    public String getMovieId() { return movieId; }
    public String getMovieTitle() { return movieTitle; }
    public String getShowtimeId() { return showtimeId; }
    public String getHallName() { return hallName; }
    public String getShowtime() { return showtime; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public int getQuantity() { return quantity; }
    public double getTotalPrice() { return totalPrice; }
    public LocalDateTime getPurchaseTime() { return purchaseTime; }
    public Status getStatus() { return status; }

    public void setStatus(Status status) { this.status = status; }
}
