package concert.reservation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeatReservationStateTest {
    @Test
    void followsTheReservationLifecycle() {
        SeatReservation reservation = new SeatReservation(1, 100, 15, 12000);
        assertEquals("NEW", reservation.getStatus());
        reservation.reserve();
        assertEquals("RESERVED", reservation.getStatus());
        reservation.release();
        assertEquals("RELEASED", reservation.getStatus());
    }

    @Test
    void rejectsOutOfOrderAndRepeatedTransitions() {
        SeatReservation reservation = new SeatReservation(1, 100, 15, 12000);
        assertThrows(IllegalArgumentException.class, reservation::release);
        reservation.reserve();
        assertThrows(IllegalArgumentException.class, reservation::reserve);
        reservation.release();
        assertThrows(IllegalArgumentException.class, reservation::release);
        assertThrows(IllegalArgumentException.class, reservation::reserve);
        assertEquals("RELEASED", reservation.getStatus());
    }

    @Test
    void rejectsInvalidIdentifiersSeatsPricesAndStates() {
        assertThrows(IllegalArgumentException.class, () -> new SeatReservation(0, 100, 15, 12000));
        assertThrows(IllegalArgumentException.class, () -> new SeatReservation(1, 0, 15, 12000));
        assertThrows(IllegalArgumentException.class, () -> new SeatReservation(1, 100, 0, 12000));
        assertThrows(IllegalArgumentException.class, () -> new SeatReservation(1, 100, 101, 12000));
        assertThrows(IllegalArgumentException.class, () -> new SeatReservation(1, 100, 15, 0));
        assertThrows(IllegalArgumentException.class, () -> new SeatReservation(1, 100, 15, -1));
        assertThrows(IllegalArgumentException.class, () -> new SeatReservation(1, 100, 15, 12000, "PAID"));
        assertThrows(IllegalArgumentException.class, () -> new SeatReservation(1, 100, 15, 12000, null));
    }
}
