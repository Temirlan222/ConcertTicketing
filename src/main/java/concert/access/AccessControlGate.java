package concert.access;

import concert.BookingException;

import java.sql.SQLException;
import java.util.Objects;

public final class AccessControlGate {
    private final TicketAccessRepository repository;

    public AccessControlGate(TicketAccessRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public void checkOpen(int eventId) throws SQLException {
        if (eventId <= 0) {
            throw new BookingException("Event ID must be positive");
        }
        if (!repository.isGateOpen(eventId)) {
            throw new BookingException("Access gate is closed for event " + eventId);
        }
    }
}
