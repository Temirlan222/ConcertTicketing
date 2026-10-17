package concert.processing;

import java.sql.SQLException;
import java.util.Objects;

public class TicketPrinter {
    private final FraudChecker fraudChecker;

    public TicketPrinter(FraudChecker fraudChecker) {
        this.fraudChecker = Objects.requireNonNull(fraudChecker);
    }

    public TicketProcessing issue(int id, String ticketCode) throws SQLException {
        fraudChecker.check(ticketCode);
        TicketProcessing ticket = new TicketProcessing(id, ticketCode);
        ticket.print();
        return ticket;
    }
}
