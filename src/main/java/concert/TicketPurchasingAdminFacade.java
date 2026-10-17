package concert;

import concert.reservation.ReservationRepository;
import concert.processing.TicketProcessingRepository;
import concert.access.TicketAccessRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class TicketPurchasingAdminFacade {
    private final Database database;
    private final WorkflowRepository workflows;
    private final TicketPurchasingFacade purchasing;

    public TicketPurchasingAdminFacade(Database database) {
        this.database = database;
        workflows = new WorkflowRepository(database.connection());
        purchasing = new TicketPurchasingFacade(database);
    }

    public Workflow getPersistedState(int id) throws SQLException {
        return workflows.find(id).orElseThrow(() -> new BookingException("Unknown workflow: " + id));
    }

    public List<Workflow> listPurchases() throws SQLException {
        return workflows.all();
    }

    public Workflow startCompensation(int id) throws SQLException {
        return purchasing.startCompensation(id);
    }

    public void setGateOpen(int eventId, boolean open) throws SQLException {
        new TicketAccessRepository(database.connection()).setGateOpen(eventId, open);
    }

    public List<Workflow> recoverAll() throws SQLException {
        var recovered = new ArrayList<Workflow>();
        for (var w : workflows.all()) {
            if (!w.isTerminal()) {
                recovered.add(purchasing.resume(w.id()));
            }
        }
        return recovered;
    }

    public String inspect() throws SQLException {
        var out = new StringBuilder("WORKFLOWS\n");
        for (var w : workflows.all()) {
            out.append(w)
                    .append(" events=")
                    .append(workflows.events(w.id()))
                    .append('\n');
        }
        out.append("A RESERVATIONS\n");
        for (var r : new ReservationRepository(database.connection()).all()) {
            out.append(r.reservationId)
                    .append(" event=")
                    .append(r.eventId)
                    .append(" seat=")
                    .append(r.seatId)
                    .append(" price=")
                    .append(r.price)
                    .append(' ')
                    .append(r.getStatus())
                    .append('\n');
        }
        out.append("B TICKETS\n");
        for (var t : new TicketProcessingRepository(database.connection()).all()) {
            out.append(t.id)
                    .append(' ')
                    .append(t.ticketCode)
                    .append(' ')
                    .append(t.getStatus())
                    .append('\n');
        }
        out.append("C ACCESS / LOCAL REFUND CREDIT (KZT)\n");
        for (var c : new TicketAccessRepository(database.connection()).all()) {
            out.append(c.id)
                    .append(' ')
                    .append(c.ticketCode)
                    .append(" event=")
                    .append(c.eventId)
                    .append(' ')
                    .append(c.getStatus())
                    .append(" refund=")
                    .append(c.getRefundedAmount())
                    .append('\n');
        }
        return out.toString();
    }
}
