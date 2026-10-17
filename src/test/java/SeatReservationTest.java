import concert.reservation.SeatReservation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SeatReservationTest {

    @Test
    void newReservationShouldHaveNewStatus() {
        SeatReservation reservation =
                new SeatReservation(1, 100, 15, 12000);

        assertEquals("NEW", reservation.getStatus());
    }
}
