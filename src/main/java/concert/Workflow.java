package concert;

/** Each subsystem entity uses this purchase id as a correlation id, without knowing Workflow. */
public record Workflow(int id, int eventId, int seatId, String ticketCode, Status status, String failureReason) {
    public enum Status {
        PENDING_A, PENDING_B, PENDING_C, COMPENSATING_B, COMPENSATING_A, COMPLETED, COMPENSATED
    }

    public boolean isTerminal() {
        return status == Status.COMPLETED || status == Status.COMPENSATED;
    }

    public Workflow transition(Status next, String reason) {
        boolean allowed = switch (status) {
            case PENDING_A -> next == Status.PENDING_B || next == Status.COMPENSATED;
            case PENDING_B -> next == Status.PENDING_C || next == Status.COMPENSATING_A;
            case PENDING_C -> next == Status.COMPLETED || next == Status.COMPENSATING_B;
            case COMPENSATING_B -> next == Status.COMPENSATING_A;
            case COMPENSATING_A -> next == Status.COMPENSATED;
            case COMPLETED, COMPENSATED -> false;
        };
        if (!allowed) {
            throw new BookingException("Illegal workflow transition: " + status + " -> " + next);
        }
        return new Workflow(id, eventId, seatId, ticketCode, next, reason);
    }
}
