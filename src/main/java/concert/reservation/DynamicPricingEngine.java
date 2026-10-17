package concert.reservation;

import concert.BookingException;

/** Fixed concert pricing in KZT: premium seats 1-10, standard seats 11-100. */
public class DynamicPricingEngine {
    public int priceFor(int seatId) {
        if (seatId < 1 || seatId > 100) {
            throw new BookingException("Seat must be between 1 and 100");
        }
        return seatId <= 10 ? 15000 : 12000;
    }
}
