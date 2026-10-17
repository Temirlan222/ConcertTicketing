package concert.processing;

import concert.BookingException;

public class TicketProcessing {
    public final int id;
    public final String ticketCode;
    private String status;

    public TicketProcessing(int id, String ticketCode) {
        this(id, ticketCode, "PENDING");
    }

    public TicketProcessing(int id, String ticketCode, String status) {
        if (id <= 0) {
            throw new BookingException("Ticket id must be positive");
        }
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new BookingException("Ticket code is required");
        }
        if (!"PENDING".equals(status) && !"PRINTED".equals(status) && !"VOIDED".equals(status)) {
            throw new BookingException("Invalid ticket status: " + status);
        }
        this.id = id;
        this.ticketCode = ticketCode.trim();
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void print() {
        if (!"PENDING".equals(status)) {
            throw new BookingException("Only a pending ticket can be printed");
        }
        status = "PRINTED";
    }

    public void voidTicket() {
        if (!"PRINTED".equals(status)) {
            throw new BookingException("Only a printed ticket can be voided");
        }
        status = "VOIDED";
    }
}
