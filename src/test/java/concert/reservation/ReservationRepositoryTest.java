package concert.reservation;

import concert.Database;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class ReservationRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsFieldsAndStateAfterReopeningTheDatabase() throws Exception {
        Path file = tempDir.resolve("reservations.db");
        try (Database database = new Database(file)) {
            ReservationRepository repository = new ReservationRepository(database.connection());
            SeatReservation reservation = new SeatReservation(1, 100, 15, 12000);
            reservation.reserve();
            repository.save(reservation);
        }
        try (Database database = new Database(file)) {
            ReservationRepository repository = new ReservationRepository(database.connection());
            SeatReservation loaded = repository.find(1).orElseThrow();
            assertEquals(1, loaded.reservationId);
            assertEquals(100, loaded.eventId);
            assertEquals(15, loaded.seatId);
            assertEquals(12000, loaded.price);
            assertEquals("RESERVED", loaded.getStatus());
            assertTrue(repository.find(99).isEmpty());
        }
    }

    @Test
    void updatesExistingReservationAndFreesAReleasedSeat() throws Exception {
        try (Database database = new Database(tempDir.resolve("release.db"))) {
            ReservationRepository repository = new ReservationRepository(database.connection());
            SeatReservation reservation = new SeatReservation(1, 100, 15, 12000);
            repository.save(reservation);
            assertFalse(repository.isReserved(100, 15));
            reservation.reserve();
            repository.save(reservation);
            assertTrue(repository.isReserved(100, 15));
            assertFalse(repository.isReserved(101, 15));
            reservation.release();
            repository.save(reservation);
            assertFalse(repository.isReserved(100, 15));
            assertEquals(1, repository.all().size());
            assertEquals("RELEASED", repository.find(1).orElseThrow().getStatus());
            repository.save(new SeatReservation(2, 100, 15, 12000, "RESERVED"));
            assertTrue(repository.isReserved(100, 15));
            assertEquals(2, repository.all().size());
        }
    }

    @Test
    void databaseRejectsTwoActiveReservationsForOneEventAndSeat() throws Exception {
        try (Database database = new Database(tempDir.resolve("unique.db"))) {
            ReservationRepository repository = new ReservationRepository(database.connection());
            repository.save(new SeatReservation(1, 100, 15, 12000, "RESERVED"));
            assertThrows(SQLException.class,
                    () -> repository.save(new SeatReservation(2, 100, 15, 12000, "RESERVED")));
            repository.save(new SeatReservation(3, 101, 15, 12000, "RESERVED"));
            assertEquals(2, repository.all().size());
        }
    }
}
