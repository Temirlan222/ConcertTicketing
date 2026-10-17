package concert.access;

import concert.BookingException;

public final class TicketAccess {
    public final int id;
    public final String ticketCode;
    public final int eventId;
    public final int amount;
    private String status;
    private int refundedAmount;

    public TicketAccess(int id, String ticketCode, int eventId, int amount) {
        this(id, ticketCode, eventId, amount, "PENDING", 0);
    }

    public TicketAccess(int id, String ticketCode, int eventId, int amount,
                        String status, int refundedAmount) {
        if (id <= 0 || eventId <= 0 || amount <= 0 || ticketCode == null || ticketCode.isBlank()) {
            throw new BookingException("Access needs positive IDs, a ticket code and a positive amount");
        }
        if (!"PENDING".equals(status) && !"APPROVED".equals(status)
                && !"REJECTED".equals(status) && !"REFUNDED".equals(status)) {
            throw new BookingException("Unknown access status");
        }
        if (("REFUNDED".equals(status) && refundedAmount != amount)
                || (!"REFUNDED".equals(status) && refundedAmount != 0)) {
            throw new BookingException("Refund amount is inconsistent with access status");
        }
        this.id = id;
        this.ticketCode = ticketCode;
        this.eventId = eventId;
        this.amount = amount;
        this.status = status;
        this.refundedAmount = refundedAmount;
    }

    public String getStatus() {
        return status;
    }

    public int getRefundedAmount() {
        return refundedAmount;
    }

    public void approve() {
        requireStatus("PENDING");
        status = "APPROVED";
    }

    public void reject() {
        requireStatus("PENDING");
        status = "REJECTED";
    }

    public void refund() {
        requireStatus("REJECTED");
        refundedAmount = amount;
        status = "REFUNDED";
    }

    private void requireStatus(String expected) {
        if (!expected.equals(status)) {
            throw new BookingException("Access must be " + expected + "; current status is " + status);
        }
    }
}
