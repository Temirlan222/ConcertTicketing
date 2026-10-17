package concert.access;

import concert.BookingException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TicketAccessRepository {
    private final Connection connection;

    public TicketAccessRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    public void save(TicketAccess access) throws SQLException {
        String sql = "INSERT INTO ticket_access (id,ticket_code,event_id,amount,status,refunded_amount) "
                + "VALUES (?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
                + "ticket_code=excluded.ticket_code,event_id=excluded.event_id,amount=excluded.amount,"
                + "status=excluded.status,refunded_amount=excluded.refunded_amount";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, access.id);
            statement.setString(2, access.ticketCode);
            statement.setInt(3, access.eventId);
            statement.setInt(4, access.amount);
            statement.setString(5, access.getStatus());
            statement.setInt(6, access.getRefundedAmount());
            statement.executeUpdate();
        }
    }

    public Optional<TicketAccess> find(int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM ticket_access WHERE id=?")) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(read(rows)) : Optional.empty();
            }
        }
    }

    public List<TicketAccess> all() throws SQLException {
        List<TicketAccess> accesses = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM ticket_access ORDER BY id");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                accesses.add(read(rows));
            }
        }
        return accesses;
    }

    public boolean isGateOpen(int eventId) throws SQLException {
        requireEventId(eventId);
        try (PreparedStatement statement = connection.prepareStatement("SELECT is_open FROM gate_settings WHERE event_id=?")) {
            statement.setInt(1, eventId);
            try (ResultSet rows = statement.executeQuery()) {
                return !rows.next() || rows.getInt("is_open") != 0;
            }
        }
    }

    public void setGateOpen(int eventId, boolean open) throws SQLException {
        requireEventId(eventId);
        String sql = "INSERT INTO gate_settings (event_id,is_open) VALUES (?,?) "
                + "ON CONFLICT(event_id) DO UPDATE SET is_open=excluded.is_open";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, eventId);
            statement.setInt(2, open ? 1 : 0);
            statement.executeUpdate();
        }
    }

    private TicketAccess read(ResultSet rows) throws SQLException {
        return new TicketAccess(rows.getInt("id"), rows.getString("ticket_code"),
                rows.getInt("event_id"), rows.getInt("amount"),
                rows.getString("status"), rows.getInt("refunded_amount"));
    }

    private void requireEventId(int eventId) {
        if (eventId <= 0) {
            throw new BookingException("Event ID must be positive");
        }
    }
}
