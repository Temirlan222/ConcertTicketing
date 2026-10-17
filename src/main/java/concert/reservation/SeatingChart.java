package concert.reservation;

import concert.BookingException;

import java.sql.SQLException;
import java.util.Objects;

/** Validates event/seat inputs and reads current seat availability. */
public class SeatingChart {
    private final ReservationRepository repository;

    public SeatingChart(ReservationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void checkAvailable(int eventId, int seatId) throws SQLException {
        if (eventId <= 0) {
            throw new BookingException("Event ID must be positive");
        }
        if (seatId < 1 || seatId > 100) {
            throw new BookingException("Seat must be between 1 and 100");
        }
        if (repository.isReserved(eventId, seatId)) {
            throw new BookingException("Seat " + seatId + " is already reserved for event " + eventId);
        }
    }
}
