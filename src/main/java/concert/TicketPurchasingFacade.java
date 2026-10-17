package concert;

import concert.reservation.*;
import concert.processing.*;
import concert.access.*;

import java.sql.Connection;
import java.sql.SQLException;

import static concert.Workflow.Status.*;

/** Durable orchestration. Every call to advanceOne commits at most ONE stage. */
public final class TicketPurchasingFacade {
    private final Connection connection;
    private final WorkflowRepository workflows;
    private final ReservationRepository reservations;
    private final TicketProcessingRepository tickets;
    private final TicketAccessRepository access;
    private final SeatingChart seating;
    private final DynamicPricingEngine pricing = new DynamicPricingEngine();
    private final TicketPrinter printer;
    private final AccessControlGate gate;
    private final RefundService refunds = new RefundService();

    public TicketPurchasingFacade(Database database) {
        connection = database.connection();
        workflows = new WorkflowRepository(connection);
        reservations = new ReservationRepository(connection);
        tickets = new TicketProcessingRepository(connection);
        access = new TicketAccessRepository(connection);
        seating = new SeatingChart(reservations);
        printer = new TicketPrinter(new FraudChecker(tickets));
        gate = new AccessControlGate(access);
    }

    public Workflow buyTicket(int eventId, int seatId, String ticketCode) throws SQLException {
        return resume(startPurchase(eventId, seatId, ticketCode).id());
    }

    /** Persist user input; useful for a deliberate restart demonstration. */
    public Workflow startPurchase(int eventId, int seatId, String ticketCode) throws SQLException {
        if (!connection.getAutoCommit()) {
            throw new SQLException("A purchase requires its own transaction");
        }
        connection.setAutoCommit(false);
        try {
            seating.checkAvailable(eventId, seatId);
            if (ticketCode == null || ticketCode.isBlank()) {
                throw new BookingException("Ticket code must not be blank");
            }
            var w = workflows.create(nextPurchaseId(), eventId, seatId, ticketCode.trim());
            connection.commit();
            return w;
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    // The facade alone knows all three id namespaces; direct subsystem records stay intact.
    private int nextPurchaseId() throws SQLException {
        int max = workflows.all().stream().mapToInt(Workflow::id).max().orElse(0);
        max = Math.max(max, reservations.all().stream().mapToInt(r -> r.reservationId).max().orElse(0));
        max = Math.max(max, tickets.all().stream().mapToInt(t -> t.id).max().orElse(0));
        max = Math.max(max, access.all().stream().mapToInt(c -> c.id).max().orElse(0));
        return Math.incrementExact(max);
    }

    public Workflow resume(int id) throws SQLException {
        var w = load(id);
        while (!w.isTerminal()) {
            w = advanceOne(id);
        }
        return w;
    }

    public Workflow advanceOne(int id) throws SQLException {
        if (!connection.getAutoCommit()) {
            throw new SQLException("A stage requires its own transaction");
        }
        connection.setAutoCommit(false);
        try {
            var w = load(id);
            switch (w.status()) {
                case PENDING_A -> reserve(w);
                case PENDING_B -> print(w);
                case PENDING_C -> finalizeAccess(w);
                case COMPENSATING_B -> voidTicket(w);
                case COMPENSATING_A -> release(w);
                case COMPLETED, COMPENSATED -> {
                }
            }
            connection.commit();
            return load(id);
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /** Completed purchases are deliberately outside manual-compensation scope. */
    public Workflow startCompensation(int id) throws SQLException {
        if (!connection.getAutoCommit()) {
            throw new SQLException("Compensation requires its own transaction");
        }
        connection.setAutoCommit(false);
        try {
            var w = load(id);
            switch (w.status()) {
                case PENDING_A -> workflows.save(w.transition(COMPENSATED, "Cancelled before reservation"));
                case PENDING_B -> workflows.save(w.transition(COMPENSATING_A, "Manual compensation"));
                case PENDING_C -> workflows.save(w.transition(COMPENSATING_B, "Manual compensation"));
                case COMPLETED -> throw new BookingException("Completed purchase cannot be manually compensated");
                case COMPENSATING_B, COMPENSATING_A, COMPENSATED -> {
                }
            }
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
        return resume(id);
    }

    private Workflow load(int id) throws SQLException {
        return workflows.find(id).orElseThrow(() -> new BookingException("Unknown workflow: " + id));
    }

    private void reserve(Workflow w) throws SQLException {
        try {
            seating.checkAvailable(w.eventId(), w.seatId());
        } catch (BookingException e) {
            workflows.save(w.transition(COMPENSATED, e.getMessage()));
            workflows.event(w.id(), "A_REJECTED");
            return;
        }
        var reservation = new SeatReservation(w.id(), w.eventId(), w.seatId(), pricing.priceFor(w.seatId()));
        reservation.reserve();
        reservations.save(reservation);
        workflows.event(w.id(), "A_RESERVED");
        workflows.save(w.transition(PENDING_B, ""));
    }

    private void print(Workflow w) throws SQLException {
        var reservation = loadReservation(w);
        TicketProcessing ticket;
        try {
            if (!"RESERVED".equals(reservation.getStatus())) {
                throw new BookingException("Saved reservation is no longer active");
            }
            ticket = printer.issue(w.id(), w.ticketCode());
        } catch (BookingException e) {
            workflows.event(w.id(), "B_REJECTED");
            workflows.save(w.transition(COMPENSATING_A, e.getMessage()));
            return;
        }
        tickets.save(ticket);
        workflows.event(w.id(), "B_PRINTED");
        workflows.save(w.transition(PENDING_C, ""));
    }

    private void finalizeAccess(Workflow w) throws SQLException {
        var reservation = loadReservation(w);
        var ticket = loadTicket(w);
        var c = new TicketAccess(w.id(), w.ticketCode(), w.eventId(), reservation.price);
        try {
            if (!"RESERVED".equals(reservation.getStatus()) || !"PRINTED".equals(ticket.getStatus())) {
                throw new BookingException("An earlier saved stage is no longer active");
            }
            gate.checkOpen(w.eventId());
        } catch (BookingException e) {
            c.reject();
            refunds.refund(c);
            access.save(c);
            workflows.event(w.id(), "C_REFUNDED");
            workflows.save(w.transition(COMPENSATING_B, e.getMessage()));
            return;
        }
        c.approve();
        access.save(c);
        workflows.event(w.id(), "C_APPROVED");
        workflows.save(w.transition(COMPLETED, ""));
    }

    private void voidTicket(Workflow w) throws SQLException {
        var ticket = loadTicket(w);
        if (!"VOIDED".equals(ticket.getStatus())) {
            ticket.voidTicket();
            tickets.save(ticket);
            workflows.event(w.id(), "B_VOIDED");
        }
        workflows.save(w.transition(COMPENSATING_A, w.failureReason()));
    }

    private void release(Workflow w) throws SQLException {
        var reservation = loadReservation(w);
        if (!"RELEASED".equals(reservation.getStatus())) {
            reservation.release();
            reservations.save(reservation);
            workflows.event(w.id(), "A_RELEASED");
        }
        workflows.save(w.transition(COMPENSATED, w.failureReason()));
    }

    private SeatReservation loadReservation(Workflow w) throws SQLException {
        var reservation = reservations.find(w.id())
                .orElseThrow(() -> new BookingException("Saved reservation missing"));
        if (reservation.eventId != w.eventId() || reservation.seatId != w.seatId()) {
            throw new BookingException("Saved reservation does not match workflow");
        }
        return reservation;
    }

    private TicketProcessing loadTicket(Workflow w) throws SQLException {
        var ticket = tickets.find(w.id()).orElseThrow(() -> new BookingException("Saved ticket missing"));
        if (!ticket.ticketCode.equals(w.ticketCode())) {
            throw new BookingException("Saved ticket does not match workflow");
        }
        return ticket;
    }
}
