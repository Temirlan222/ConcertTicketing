package concert.access;

public final class RefundService {
    /** Records a full local credit in KZT; no external payment is performed. */
    public void refund(TicketAccess access) {
        access.refund();
    }
}
