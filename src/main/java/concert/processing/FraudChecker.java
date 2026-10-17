package concert.processing;

import concert.BookingException;

import java.sql.SQLException;
import java.util.Objects;

public class FraudChecker {
    private final TicketProcessingRepository repository;

    public FraudChecker(TicketProcessingRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public void check(String ticketCode) throws SQLException {
        if (ticketCode == null || ticketCode.isBlank()) {
            throw new BookingException("Ticket code is required");
        }
        if (repository.existsTicketCode(ticketCode.trim())) {
            throw new BookingException("Ticket code has already been used: " + ticketCode);
        }
    }
}
