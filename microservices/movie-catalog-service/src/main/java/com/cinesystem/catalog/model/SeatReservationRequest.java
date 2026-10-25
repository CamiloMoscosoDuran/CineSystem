package com.cinesystem.catalog.model;

/**
 * DTO para la solicitud de reserva de asientos.
 */
public class SeatReservationRequest {
    private String showtimeId;
    private int quantity;
    private String customerName;

    public SeatReservationRequest() {}

    public SeatReservationRequest(String showtimeId, int quantity, String customerName) {
        this.showtimeId = showtimeId;
        this.quantity = quantity;
        this.customerName = customerName;
    }

    public String getShowtimeId() { return showtimeId; }
    public void setShowtimeId(String showtimeId) { this.showtimeId = showtimeId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
}
