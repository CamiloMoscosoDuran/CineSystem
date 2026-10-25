package com.cinesystem.ticketing.model;

/**
 * DTO para solicitud de cancelación de boleto.
 */
public class CancelRequest {
    private String ticketId;
    private String reason;

    public CancelRequest() {}

    public String getTicketId() { return ticketId; }
    public void setTicketId(String ticketId) { this.ticketId = ticketId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
