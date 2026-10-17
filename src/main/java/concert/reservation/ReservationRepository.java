package concert.reservation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** SQLite persistence for reservations; transaction ownership stays with the caller. */
public class ReservationRepository {
    private final Connection connection;

    public ReservationRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
    }

    public void save(SeatReservation reservation) throws SQLException {
        Objects.requireNonNull(reservation, "reservation");
        String sql = "INSERT INTO seat_reservations(id,event_id,seat_id,price,status) VALUES(?,?,?,?,?) "
                + "ON CONFLICT(id) DO UPDATE SET event_id=excluded.event_id,seat_id=excluded.seat_id,"
                + "price=excluded.price,status=excluded.status";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reservation.reservationId);
            statement.setInt(2, reservation.eventId);
            statement.setInt(3, reservation.seatId);
            statement.setInt(4, reservation.price);
            statement.setString(5, reservation.getStatus());
            statement.executeUpdate();
        }
    }

    public Optional<SeatReservation> find(int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,event_id,seat_id,price,status FROM seat_reservations WHERE id=?")) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(read(result)) : Optional.empty();
            }
        }
    }

    public List<SeatReservation> all() throws SQLException {
        List<SeatReservation> reservations = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id,event_id,seat_id,price,status FROM seat_reservations ORDER BY id");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                reservations.add(read(result));
            }
        }
        return reservations;
    }

    public boolean isReserved(int eventId, int seatId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM seat_reservations WHERE event_id=? AND seat_id=? AND status='RESERVED'")) {
            statement.setInt(1, eventId);
            statement.setInt(2, seatId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private SeatReservation read(ResultSet result) throws SQLException {
        return new SeatReservation(result.getInt("id"), result.getInt("event_id"),
                result.getInt("seat_id"), result.getInt("price"), result.getString("status"));
    }
}
