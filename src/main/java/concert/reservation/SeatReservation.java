package concert.reservation;

/** A reservation with a one-way NEW -> RESERVED -> RELEASED lifecycle. */
public class SeatReservation {
    public final int reservationId;
    public final int eventId;
    public final int seatId;
    public final int price;
    private String status;

    public SeatReservation(int reservationId, int eventId, int seatId, int price) {
        this(reservationId, eventId, seatId, price, "NEW");
    }

    public SeatReservation(int reservationId, int eventId, int seatId, int price, String status) {
        if (reservationId <= 0 || eventId <= 0) {
            throw new IllegalArgumentException("Reservation and event IDs must be positive");
        }
        if (seatId < 1 || seatId > 100) {
            throw new IllegalArgumentException("Seat must be between 1 and 100");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (!"NEW".equals(status) && !"RESERVED".equals(status) && !"RELEASED".equals(status)) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
        this.reservationId = reservationId;
        this.eventId = eventId;
        this.seatId = seatId;
        this.price = price;
        this.status = status;
    }

    public void reserve() {
        if (!"NEW".equals(status)) {
            throw new IllegalArgumentException("Only a new reservation can be reserved");
        }
        status = "RESERVED";
    }

    public void release() {
        if (!"RESERVED".equals(status)) {
            throw new IllegalArgumentException("Only a reserved seat can be released");
        }
        status = "RELEASED";
    }

    public String getStatus() {
        return status;
    }
}
