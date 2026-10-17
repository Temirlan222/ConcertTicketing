package concert;

/** A domain refusal that can be displayed to the CLI user. */
public class BookingException extends RuntimeException {
    public BookingException(String message) { super(message); }
    public BookingException(String message, Throwable cause) { super(message, cause); }
}
