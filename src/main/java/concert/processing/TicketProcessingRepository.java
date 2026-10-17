package concert.processing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TicketProcessingRepository {
    private final Connection connection;

    public TicketProcessingRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    public void save(TicketProcessing ticket) throws SQLException {
        String sql = "INSERT INTO ticket_processing (id, ticket_code, status) VALUES (?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET status = excluded.status "
                + "WHERE ticket_processing.ticket_code = excluded.ticket_code";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ticket.id);
            statement.setString(2, ticket.ticketCode);
            statement.setString(3, ticket.getStatus());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Ticket id already belongs to a different ticket code: " + ticket.id);
            }
        }
    }

    public Optional<TicketProcessing> find(int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, ticket_code, status FROM ticket_processing WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? Optional.of(readTicket(results)) : Optional.empty();
            }
        }
    }

    public List<TicketProcessing> all() throws SQLException {
        List<TicketProcessing> tickets = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, ticket_code, status FROM ticket_processing ORDER BY id");
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                tickets.add(readTicket(results));
            }
        }
        return tickets;
    }

    public boolean existsTicketCode(String ticketCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM ticket_processing WHERE ticket_code = ?")) {
            statement.setString(1, ticketCode == null ? null : ticketCode.trim());
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    private TicketProcessing readTicket(ResultSet results) throws SQLException {
        return new TicketProcessing(results.getInt("id"), results.getString("ticket_code"),
                results.getString("status"));
    }
}
