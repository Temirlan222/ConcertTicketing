package concert.processing;

import concert.BookingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketProcessingRepositoryTest {
    @TempDir
    Path directory;

    private Connection connection;
    private TicketProcessingRepository repository;

    @BeforeEach
    void openDatabase() throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("processing.db"));
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE ticket_processing (id INTEGER PRIMARY KEY, "
                    + "ticket_code TEXT UNIQUE NOT NULL, status TEXT)");
        }
        repository = new TicketProcessingRepository(connection);
    }

    @AfterEach
    void closeDatabase() throws SQLException {
        connection.close();
    }

    @Test
    void issueChecksCodeAndReturnsPrintedTicketWithoutSavingIt() throws SQLException {
        TicketPrinter printer = new TicketPrinter(new FraudChecker(repository));
        TicketProcessing ticket = printer.issue(1, "TICKET-1");

        assertEquals(1, ticket.id);
        assertEquals("TICKET-1", ticket.ticketCode);
        assertEquals("PRINTED", ticket.getStatus());
        assertTrue(repository.all().isEmpty());
    }

    @Test
    void savedTicketCanBeLoadedAfterDatabaseReopens() throws SQLException {
        TicketProcessing ticket = new TicketProcessing(1, "TICKET-1");
        ticket.print();
        repository.save(ticket);
        connection.close();

        connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("processing.db"));
        repository = new TicketProcessingRepository(connection);
        TicketProcessing restored = repository.find(1).orElseThrow();
        assertEquals(1, restored.id);
        assertEquals("TICKET-1", restored.ticketCode);
        assertEquals("PRINTED", restored.getStatus());
    }

    @Test
    void saveUpdatesStatusWithoutAddingAnotherTicket() throws SQLException {
        TicketProcessing ticket = new TicketProcessing(1, "TICKET-1", "PRINTED");
        repository.save(ticket);
        ticket.voidTicket();
        repository.save(ticket);

        assertEquals(1, repository.all().size());
        assertEquals("VOIDED", repository.find(1).orElseThrow().getStatus());
    }

    @Test
    void fraudCheckRejectsBothPrintedAndVoidedCodes() throws SQLException {
        TicketProcessing ticket = new TicketProcessing(1, "TICKET-1", "PRINTED");
        repository.save(ticket);
        TicketPrinter printer = new TicketPrinter(new FraudChecker(repository));
        assertThrows(BookingException.class, () -> printer.issue(2, "TICKET-1"));

        ticket.voidTicket();
        repository.save(ticket);
        assertTrue(repository.existsTicketCode("TICKET-1"));
        assertThrows(BookingException.class, () -> printer.issue(2, "TICKET-1"));
        assertEquals("VOIDED", repository.find(1).orElseThrow().getStatus());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void fraudCheckRejectsBlankCode(String code) {
        FraudChecker checker = new FraudChecker(repository);
        assertThrows(BookingException.class, () -> checker.check(code));
    }

    @Test
    void unusedCodePassesFraudCheck() throws SQLException {
        new FraudChecker(repository).check("TICKET-1");
        assertFalse(repository.existsTicketCode("TICKET-1"));
    }

    @Test
    void missingIdReturnsEmptyAndAllReturnsTicketsInIdOrder() throws SQLException {
        assertTrue(repository.find(100).isEmpty());
        repository.save(new TicketProcessing(2, "TICKET-2", "VOIDED"));
        repository.save(new TicketProcessing(1, "TICKET-1", "PRINTED"));

        assertEquals(2, repository.all().size());
        assertEquals(1, repository.all().get(0).id);
        assertEquals("VOIDED", repository.all().get(1).getStatus());
    }

    @Test
    void duplicateCodeCannotBeSavedUnderAnotherId() throws SQLException {
        repository.save(new TicketProcessing(1, "TICKET-1", "PRINTED"));
        assertThrows(SQLException.class,
                () -> repository.save(new TicketProcessing(2, "TICKET-1", "PRINTED")));
        assertEquals(1, repository.all().size());
    }

    @Test
    void existingIdCannotReplaceHistoricalTicketCode() throws SQLException {
        repository.save(new TicketProcessing(1, "TICKET-1", "VOIDED"));
        assertThrows(SQLException.class,
                () -> repository.save(new TicketProcessing(1, "TICKET-2", "PRINTED")));
        assertTrue(repository.existsTicketCode("TICKET-1"));
        assertFalse(repository.existsTicketCode("TICKET-2"));
    }

    @Test
    void sqlFailurePropagatesFromFraudCheck() throws SQLException {
        connection.close();
        assertThrows(SQLException.class, () -> new FraudChecker(repository).check("TICKET-1"));
    }

    @Test
    void surroundingWhitespaceCannotBypassFraudDetection() throws SQLException {
        repository.save(new TicketProcessing(1, "  NORMALIZED  ", "PRINTED"));
        assertEquals("NORMALIZED", repository.find(1).orElseThrow().ticketCode);
        assertTrue(repository.existsTicketCode(" NORMALIZED "));
        assertThrows(BookingException.class, () -> new TicketPrinter(new FraudChecker(repository)).issue(2, "NORMALIZED"));
        assertThrows(BookingException.class, () -> new TicketPrinter(new FraudChecker(repository)).issue(2, " NORMALIZED "));
    }
}
