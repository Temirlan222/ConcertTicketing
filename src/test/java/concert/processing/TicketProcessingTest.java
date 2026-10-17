package concert.processing;

import concert.BookingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TicketProcessingTest {
    @Test
    void pendingTicketCanBePrintedAndVoided() {
        TicketProcessing ticket = new TicketProcessing(1, "TICKET-1");
        assertEquals("PENDING", ticket.getStatus());

        ticket.print();
        assertEquals("PRINTED", ticket.getStatus());

        ticket.voidTicket();
        assertEquals("VOIDED", ticket.getStatus());
    }

    @Test
    void pendingTicketCannotBeVoided() {
        TicketProcessing ticket = new TicketProcessing(1, "TICKET-1");
        assertThrows(BookingException.class, ticket::voidTicket);
        assertEquals("PENDING", ticket.getStatus());
    }

    @Test
    void printedTicketCannotBePrintedAgain() {
        TicketProcessing ticket = new TicketProcessing(1, "TICKET-1", "PRINTED");
        assertThrows(BookingException.class, ticket::print);
        assertEquals("PRINTED", ticket.getStatus());
    }

    @Test
    void voidedTicketCannotBePrintedOrVoidedAgain() {
        TicketProcessing ticket = new TicketProcessing(1, "TICKET-1", "VOIDED");
        assertThrows(BookingException.class, ticket::print);
        assertThrows(BookingException.class, ticket::voidTicket);
        assertEquals("VOIDED", ticket.getStatus());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonpositiveId(int id) {
        assertThrows(BookingException.class, () -> new TicketProcessing(id, "TICKET-1"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankTicketCode(String ticketCode) {
        assertThrows(BookingException.class, () -> new TicketProcessing(1, ticketCode));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"NEW", "printed", " "})
    void rejectsUnknownRestoredStatus(String status) {
        assertThrows(BookingException.class, () -> new TicketProcessing(1, "TICKET-1", status));
    }
}
