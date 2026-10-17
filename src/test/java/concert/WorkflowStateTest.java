package concert;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static concert.Workflow.Status.*;

class WorkflowStateTest {
    @Test void workflowRejectsSkippingStagesAndReopeningTerminalState() {
        var w=new Workflow(1,100,1,"CODE",PENDING_A,"");
        assertThrows(BookingException.class,()->w.transition(COMPLETED,""));
        var done=w.transition(PENDING_B,"").transition(PENDING_C,"").transition(COMPLETED,"");
        assertTrue(done.isTerminal());
        assertThrows(BookingException.class,()->done.transition(PENDING_A,""));
        var undone=w.transition(PENDING_B,"").transition(COMPENSATING_A,"duplicate").transition(COMPENSATED,"duplicate");
        assertTrue(undone.isTerminal());
        assertThrows(BookingException.class,()->undone.transition(PENDING_B,""));
    }
}
