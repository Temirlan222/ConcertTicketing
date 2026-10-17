package concert;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WorkflowRepository {
    private final Connection connection;

    public WorkflowRepository(Connection connection) {
        this.connection = connection;
    }

    public Workflow create(int id, int eventId, int seatId, String code) throws SQLException {
        try (var s = connection.prepareStatement(
                "INSERT INTO workflows(id,event_id,seat_id,ticket_code,status) VALUES(?,?,?,?,'PENDING_A')")) {
            s.setInt(1, id);
            s.setInt(2, eventId);
            s.setInt(3, seatId);
            s.setString(4, code);
            s.executeUpdate();
        }
        return new Workflow(id, eventId, seatId, code, Workflow.Status.PENDING_A, "");
    }

    public Optional<Workflow> find(int id) throws SQLException {
        try (var s = connection.prepareStatement("SELECT * FROM workflows WHERE id=?")) {
            s.setInt(1, id);
            try (var r = s.executeQuery()) {
                if (!r.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Workflow(
                        r.getInt("id"),
                        r.getInt("event_id"),
                        r.getInt("seat_id"),
                        r.getString("ticket_code"),
                        Workflow.Status.valueOf(r.getString("status")),
                        r.getString("failure_reason")));
            }
        }
    }

    public List<Workflow> all() throws SQLException {
        var result = new ArrayList<Workflow>();
        try (var s = connection.createStatement();
             var r = s.executeQuery("SELECT * FROM workflows ORDER BY id")) {
            while (r.next()) {
                result.add(new Workflow(
                        r.getInt("id"),
                        r.getInt("event_id"),
                        r.getInt("seat_id"),
                        r.getString("ticket_code"),
                        Workflow.Status.valueOf(r.getString("status")),
                        r.getString("failure_reason")));
            }
        }
        return result;
    }

    public void save(Workflow w) throws SQLException {
        try (var s = connection.prepareStatement("UPDATE workflows SET status=?,failure_reason=? WHERE id=?")) {
            s.setString(1, w.status().name());
            s.setString(2, w.failureReason());
            s.setInt(3, w.id());
            if (s.executeUpdate() != 1) {
                throw new SQLException("Workflow not found: " + w.id());
            }
        }
    }

    public void event(int id, String action) throws SQLException {
        try (var s = connection.prepareStatement("INSERT INTO workflow_events(workflow_id,action) VALUES(?,?)")) {
            s.setInt(1, id);
            s.setString(2, action);
            s.executeUpdate();
        }
    }

    public List<String> events(int id) throws SQLException {
        var events = new ArrayList<String>();
        try (var s = connection.prepareStatement(
                "SELECT action FROM workflow_events WHERE workflow_id=? ORDER BY seq")) {
            s.setInt(1, id);
            try (var r = s.executeQuery()) {
                while (r.next()) {
                    events.add(r.getString(1));
                }
            }
        }
        return events;
    }
}
