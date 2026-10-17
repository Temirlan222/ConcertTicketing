package concert;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** One local SQLite connection; repositories own their SQL, facade owns stage transactions. */
public final class Database implements AutoCloseable {
    private final Connection connection;

    public Database(Path path) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
        try (var s = connection.createStatement()) {
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("""
                CREATE TABLE IF NOT EXISTS seat_reservations(
                  id INTEGER PRIMARY KEY, event_id INTEGER NOT NULL CHECK(event_id>0),
                  seat_id INTEGER NOT NULL CHECK(seat_id BETWEEN 1 AND 100),
                  price INTEGER NOT NULL CHECK(price>0),
                  status TEXT NOT NULL CHECK(status IN ('NEW','RESERVED','RELEASED')))
                """);
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS reserved_seat ON seat_reservations(event_id,seat_id) WHERE status='RESERVED'");
            s.execute("""
                CREATE TABLE IF NOT EXISTS ticket_processing(
                  id INTEGER PRIMARY KEY, ticket_code TEXT NOT NULL UNIQUE,
                  status TEXT NOT NULL CHECK(status IN ('PENDING','PRINTED','VOIDED')))
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS ticket_access(
                  id INTEGER PRIMARY KEY, ticket_code TEXT NOT NULL, event_id INTEGER NOT NULL,
                  amount INTEGER NOT NULL CHECK(amount>0),
                  status TEXT NOT NULL CHECK(status IN ('PENDING','APPROVED','REJECTED','REFUNDED')),
                  refunded_amount INTEGER NOT NULL CHECK(refunded_amount BETWEEN 0 AND amount))
                """);
            s.execute("CREATE TABLE IF NOT EXISTS gate_settings(event_id INTEGER PRIMARY KEY CHECK(event_id>0),is_open INTEGER NOT NULL CHECK(is_open IN (0,1)))");
            s.execute("""
                CREATE TABLE IF NOT EXISTS workflows(
                  id INTEGER PRIMARY KEY AUTOINCREMENT,event_id INTEGER NOT NULL,seat_id INTEGER NOT NULL,
                  ticket_code TEXT NOT NULL,status TEXT NOT NULL CHECK(status IN
                    ('PENDING_A','PENDING_B','PENDING_C','COMPENSATING_B','COMPENSATING_A','COMPLETED','COMPENSATED')),
                  failure_reason TEXT NOT NULL DEFAULT '')
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS workflow_events(
                  seq INTEGER PRIMARY KEY AUTOINCREMENT,workflow_id INTEGER NOT NULL REFERENCES workflows(id),
                  action TEXT NOT NULL, UNIQUE(workflow_id,action))
                """);
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
    }

    public Connection connection() { return connection; }
    @Override public void close() throws SQLException { connection.close(); }
}
