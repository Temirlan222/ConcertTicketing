package concert.access;

import concert.BookingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TicketAccessTest {
    @Test
    void pendingAccessCanBeApprovedOnlyOnce() {
        TicketAccess access = new TicketAccess(1, "TICKET-1", 100, 12000);

        assertEquals("PENDING", access.getStatus());
        access.approve();

        assertEquals("APPROVED", access.getStatus());
        assertEquals(0, access.getRefundedAmount());
        assertThrows(BookingException.class, access::approve);
        assertThrows(BookingException.class, access::reject);
        assertThrows(BookingException.class, access::refund);
        assertEquals("APPROVED", access.getStatus());
    }

    @Test
    void rejectedAccessReceivesExactlyOneFullLocalRefund() {
        TicketAccess access = new TicketAccess(2, "TICKET-2", 100, 12000);

        access.reject();
        assertEquals("REJECTED", access.getStatus());
        assertEquals(0, access.getRefundedAmount());
        assertThrows(BookingException.class, access::approve);
        assertThrows(BookingException.class, access::reject);
        new RefundService().refund(access);

        assertEquals("REFUNDED", access.getStatus());
        assertEquals(12000, access.getRefundedAmount());
        assertThrows(BookingException.class, access::approve);
        assertThrows(BookingException.class, access::reject);
        assertThrows(BookingException.class, () -> new RefundService().refund(access));
        assertEquals(12000, access.getRefundedAmount());
    }

    @Test
    void pendingAccessCannotBeRefundedBeforeRejection() {
        TicketAccess access = new TicketAccess(3, "TICKET-3", 100, 12000);

        assertThrows(BookingException.class, () -> new RefundService().refund(access));

        assertEquals("PENDING", access.getStatus());
        assertEquals(0, access.getRefundedAmount());
    }

    @Test
    void constructorRejectsInvalidIdentityAndAmount() {
        assertAll(
                () -> assertThrows(BookingException.class, () -> new TicketAccess(0, "T", 100, 12000)),
                () -> assertThrows(BookingException.class, () -> new TicketAccess(-1, "T", 100, 12000)),
                () -> assertThrows(BookingException.class, () -> new TicketAccess(1, null, 100, 12000)),
                () -> assertThrows(BookingException.class, () -> new TicketAccess(1, "  ", 100, 12000)),
                () -> assertThrows(BookingException.class, () -> new TicketAccess(1, "T", 0, 12000)),
                () -> assertThrows(BookingException.class, () -> new TicketAccess(1, "T", -1, 12000)),
                () -> assertThrows(BookingException.class, () -> new TicketAccess(1, "T", 100, 0)),
                () -> assertThrows(BookingException.class, () -> new TicketAccess(1, "T", 100, -1)));
    }

    @Test
    void restoredStatesRequireConsistentRefundAmount() {
        for (String status : new String[]{"PENDING", "APPROVED", "REJECTED"}) {
            assertEquals(status, new TicketAccess(1, "T", 100, 12000, status, 0).getStatus());
            assertThrows(BookingException.class,
                    () -> new TicketAccess(1, "T", 100, 12000, status, 12000));
        }
        TicketAccess refunded = new TicketAccess(1, "T", 100, 12000, "REFUNDED", 12000);
        assertEquals("REFUNDED", refunded.getStatus());
        assertEquals(12000, refunded.getRefundedAmount());
        assertAll(
                () -> assertThrows(BookingException.class,
                        () -> new TicketAccess(1, "T", 100, 12000, "REFUNDED", 11999)),
                () -> assertThrows(BookingException.class,
                        () -> new TicketAccess(1, "T", 100, 12000, "REFUNDED", -1)),
                () -> assertThrows(BookingException.class,
                        () -> new TicketAccess(1, "T", 100, 12000, "UNKNOWN", 0)),
                () -> assertThrows(BookingException.class,
                        () -> new TicketAccess(1, "T", 100, 12000, null, 0)));
    }
}
