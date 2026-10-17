package concert.access;

import concert.BookingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class TicketAccessRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void approvedAccessAndGateSettingsSurviveOpeningANewConnection() throws SQLException {
        Path file = directory.resolve("access.db");
        try (Connection connection = open(file)) {
            TicketAccessRepository repository = new TicketAccessRepository(connection);
            TicketAccess access = new TicketAccess(7, "TICKET-7", 100, 12000);
            access.approve();
            repository.save(access);
            repository.setGateOpen(100, false);
        }

        try (Connection connection = open(file)) {
            TicketAccessRepository repository = new TicketAccessRepository(connection);
            TicketAccess restored = repository.find(7).orElseThrow();
            assertEquals(7, restored.id);
            assertEquals("TICKET-7", restored.ticketCode);
            assertEquals(100, restored.eventId);
            assertEquals(12000, restored.amount);
            assertEquals("APPROVED", restored.getStatus());
            assertEquals(0, restored.getRefundedAmount());
            assertFalse(repository.isGateOpen(100));
            assertThrows(BookingException.class, () -> new AccessControlGate(repository).checkOpen(100));
        }
    }

    @Test
    void rejectedAccessCanBeUpdatedToPersistItsFullRefund() throws SQLException {
        Path file = directory.resolve("refund.db");
        try (Connection connection = open(file)) {
            TicketAccessRepository repository = new TicketAccessRepository(connection);
            TicketAccess access = new TicketAccess(9, "TICKET-9", 100, 15000);
            access.reject();
            repository.save(access);

            new RefundService().refund(access);
            repository.save(access);
            assertEquals(1, repository.all().size());
        }

        try (Connection connection = open(file)) {
            TicketAccess restored = new TicketAccessRepository(connection).find(9).orElseThrow();
            assertEquals("REFUNDED", restored.getStatus());
            assertEquals(15000, restored.getRefundedAmount());
            assertThrows(BookingException.class, restored::refund);
        }
    }

    @Test
    void gateIsOpenByDefaultAndCanBeClosedAndReopened() throws SQLException {
        try (Connection connection = open(directory.resolve("gates.db"))) {
            TicketAccessRepository repository = new TicketAccessRepository(connection);
            AccessControlGate gate = new AccessControlGate(repository);

            assertTrue(repository.isGateOpen(100));
            assertDoesNotThrow(() -> gate.checkOpen(100));
            repository.setGateOpen(100, false);
            assertThrows(BookingException.class, () -> gate.checkOpen(100));
            assertDoesNotThrow(() -> gate.checkOpen(101));
            repository.setGateOpen(100, true);
            assertDoesNotThrow(() -> gate.checkOpen(100));
            assertThrows(BookingException.class, () -> gate.checkOpen(0));
            assertThrows(BookingException.class, () -> gate.checkOpen(-1));
        }
    }

    @Test
    void missingAccessIsEmptyAndAllReturnsEverySavedAccess() throws SQLException {
        try (Connection connection = open(directory.resolve("list.db"))) {
            TicketAccessRepository repository = new TicketAccessRepository(connection);

            assertTrue(repository.find(1).isEmpty());
            assertTrue(repository.all().isEmpty());
            repository.save(new TicketAccess(2, "TICKET-2", 100, 18000));
            repository.save(new TicketAccess(1, "TICKET-1", 101, 12000));

            assertEquals(2, repository.all().size());
            assertEquals(1, repository.all().get(0).id);
            assertEquals(2, repository.all().get(1).id);
        }
    }

    @Test
    void repositoryWritesParticipateInTheCallersTransaction() throws SQLException {
        try (Connection connection = open(directory.resolve("rollback.db"))) {
            TicketAccessRepository repository = new TicketAccessRepository(connection);
            connection.setAutoCommit(false);

            repository.save(new TicketAccess(1, "TICKET-1", 100, 12000));
            repository.setGateOpen(100, false);
            connection.rollback();

            assertTrue(repository.find(1).isEmpty());
            assertTrue(repository.isGateOpen(100));
        }
    }

    @Test
    void databaseFailureIsNotTreatedAsAnOpenGate() throws SQLException {
        Connection connection = open(directory.resolve("closed.db"));
        AccessControlGate gate = new AccessControlGate(new TicketAccessRepository(connection));
        connection.close();

        assertThrows(SQLException.class, () -> gate.checkOpen(100));
    }

    private Connection open(Path file) throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ticket_access (id INTEGER PRIMARY KEY, "
                    + "ticket_code TEXT, event_id INTEGER, amount INTEGER, status TEXT, refunded_amount INTEGER)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS gate_settings "
                    + "(event_id INTEGER PRIMARY KEY, is_open INTEGER NOT NULL)");
        }
        return connection;
    }
}
