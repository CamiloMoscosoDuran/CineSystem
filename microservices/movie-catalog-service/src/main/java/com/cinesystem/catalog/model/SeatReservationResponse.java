package com.cinesystem.catalog.model;

/**
 * DTO para la respuesta de reserva de asientos.
 */
public class SeatReservationResponse {
    private boolean success;
    private String message;
    private String showtimeId;
    private int reservedQuantity;
    private int remainingSeats;

    public SeatReservationResponse() {}

    public SeatReservationResponse(boolean success, String message, String showtimeId,
                                    int reservedQuantity, int remainingSeats) {
        this.success = success;
        this.message = message;
        this.showtimeId = showtimeId;
        this.reservedQuantity = reservedQuantity;
        this.remainingSeats = remainingSeats;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getShowtimeId() { return showtimeId; }
    public void setShowtimeId(String showtimeId) { this.showtimeId = showtimeId; }

    public int getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(int reservedQuantity) { this.reservedQuantity = reservedQuantity; }

    public int getRemainingSeats() { return remainingSeats; }
    public void setRemainingSeats(int remainingSeats) { this.remainingSeats = remainingSeats; }
}
