package concert.reservation;

import concert.BookingException;
import concert.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SeatingAndPricingTest {
    @TempDir
    Path tempDir;

    @Test
    void chargesPremiumForFirstTenSeatsAndStandardForTheRest() {
        DynamicPricingEngine pricing = new DynamicPricingEngine();
        assertEquals(15000, pricing.priceFor(1));
        assertEquals(15000, pricing.priceFor(10));
        assertEquals(12000, pricing.priceFor(11));
        assertEquals(12000, pricing.priceFor(100));
        assertThrows(BookingException.class, () -> pricing.priceFor(0));
        assertThrows(BookingException.class, () -> pricing.priceFor(101));
    }

    @Test
    void validatesSeatAndEventAndChecksPersistedAvailability() throws Exception {
        try (Database database = new Database(tempDir.resolve("availability.db"))) {
            ReservationRepository repository = new ReservationRepository(database.connection());
            SeatingChart seating = new SeatingChart(repository);
            assertDoesNotThrow(() -> seating.checkAvailable(100, 1));
            assertDoesNotThrow(() -> seating.checkAvailable(100, 100));
            assertThrows(BookingException.class, () -> seating.checkAvailable(0, 1));
            assertThrows(BookingException.class, () -> seating.checkAvailable(100, 0));
            assertThrows(BookingException.class, () -> seating.checkAvailable(100, 101));
            repository.save(new SeatReservation(1, 100, 15, 12000, "RESERVED"));
            assertThrows(BookingException.class, () -> seating.checkAvailable(100, 15));
            assertDoesNotThrow(() -> seating.checkAvailable(101, 15));
            SeatReservation loaded = repository.find(1).orElseThrow();
            loaded.release();
            repository.save(loaded);
            assertDoesNotThrow(() -> seating.checkAvailable(100, 15));
        }
    }
}
