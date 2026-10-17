package concert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @TempDir Path directory;

    private String operate(String input,String... arguments) {
        var oldIn=System.in; var oldOut=System.out; var oldErr=System.err;
        var captured=new ByteArrayOutputStream();
        try(var output=new PrintStream(captured,true,StandardCharsets.UTF_8)) {
            System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
            System.setOut(output); System.setErr(output);
            Main.main(arguments);
        } finally { System.setIn(oldIn);System.setOut(oldOut);System.setErr(oldErr); }
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test void menuDrivesBothFailuresInspectionAndManualCompensation() throws Exception {
        Path path=directory.resolve("menu.db");
        String output=operate("""
                1
                100
                15
                FIRST
                2
                3
                100
                0
                1
                100
                16
                GATE-FAIL
                3
                100
                1
                1
                100
                17
                FIRST
                6
                100
                18
                PAUSED
                7
                4
                5
                4
                4
                2
                0
                """,path.toString(),"--no-recover");
        assertTrue(output.contains("REFUNDED"));assertTrue(output.contains("B_VOIDED"));
        try(Database db=new Database(path)) {
            assertEquals(List.of(Workflow.Status.COMPLETED,Workflow.Status.COMPENSATED,
                    Workflow.Status.COMPENSATED,Workflow.Status.COMPENSATED),
                    new WorkflowRepository(db.connection()).all().stream().map(Workflow::status).toList());
        }
    }

    @Test void startupRecoversAndInvalidInputDoesNotCreatePurchases() throws Exception {
        Path path=directory.resolve("recover.db");
        operate("6\n100\n20\nRECOVER\n0\n",path.toString(),"--no-recover");
        String output=operate("unknown\n1\nnot-a-number\n3\n100\ninvalid\n5\n999\n",path.toString());
        assertTrue(output.contains("Recovered:")); assertTrue(output.contains("Rejected:"));
        try(Database db=new Database(path)) {
            assertEquals(1,new WorkflowRepository(db.connection()).all().size());
            assertEquals(Workflow.Status.COMPLETED,new WorkflowRepository(db.connection()).find(1).orElseThrow().status());
        }
        assertDoesNotThrow(()->operate("",path.toString()));
        assertTrue(operate("",directory.resolve("missing-parent/concert.db").toString()).contains("Cannot open SQLite"));
    }
}
