package concert;

import concert.reservation.ReservationRepository;
import concert.processing.TicketProcessingRepository;
import concert.access.TicketAccessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowTest {
    @TempDir Path directory;
    Path store() { return directory.resolve("concert.db"); }

    // Domain-specific helper: tests say which seat the concertgoer buys.
    Workflow concertgoerBuysSeat(TicketPurchasingFacade facade, int seat, String code) throws Exception {
        return facade.buyTicket(100, seat, code);
    }

    @Test void successfulPurchasePersistsAllThreeStages() throws Exception {
        try (Database db = new Database(store())) {
            var facade = new TicketPurchasingFacade(db);
            var w = concertgoerBuysSeat(facade, 15, "SUCCESS");
            assertEquals(Workflow.Status.COMPLETED, w.status());
            assertEquals("RESERVED", new ReservationRepository(db.connection()).find(w.id()).orElseThrow().getStatus());
            assertEquals("PRINTED", new TicketProcessingRepository(db.connection()).find(w.id()).orElseThrow().getStatus());
            assertEquals("APPROVED", new TicketAccessRepository(db.connection()).find(w.id()).orElseThrow().getStatus());
            assertEquals(List.of("A_RESERVED", "B_PRINTED", "C_APPROVED"), new WorkflowRepository(db.connection()).events(w.id()));
            assertEquals(w, facade.resume(w.id()));
        }
    }

    @Test void duplicateCodeCompensatesOnlySavedReservation() throws Exception {
        int failed;
        try (Database db = new Database(store())) {
            var f = new TicketPurchasingFacade(db);
            concertgoerBuysSeat(f, 15, "DUPLICATE");
            var w = concertgoerBuysSeat(f, 16, "DUPLICATE"); failed = w.id();
            assertEquals(Workflow.Status.COMPENSATED, w.status());
        }
        try (Database db = new Database(store())) {
            assertEquals("RELEASED", new ReservationRepository(db.connection()).find(failed).orElseThrow().getStatus());
            assertTrue(new TicketProcessingRepository(db.connection()).find(failed).isEmpty());
            assertTrue(new TicketAccessRepository(db.connection()).find(failed).isEmpty());
            assertEquals(List.of("A_RESERVED", "B_REJECTED", "A_RELEASED"), new WorkflowRepository(db.connection()).events(failed));
        }
    }

    @Test void closedGateRefundsLocallyThenCompensatesBAndAInOrder() throws Exception {
        try (Database db = new Database(store())) {
            var admin = new TicketPurchasingAdminFacade(db);
            admin.setGateOpen(100, false);
        }
        int id;
        try (Database db = new Database(store())) {
            var w = concertgoerBuysSeat(new TicketPurchasingFacade(db), 1, "CLOSED"); id=w.id();
            assertEquals(Workflow.Status.COMPENSATED, w.status());
        }
        try (Database db = new Database(store())) {
            assertEquals("VOIDED", new TicketProcessingRepository(db.connection()).find(id).orElseThrow().getStatus());
            assertEquals("RELEASED", new ReservationRepository(db.connection()).find(id).orElseThrow().getStatus());
            var c = new TicketAccessRepository(db.connection()).find(id).orElseThrow();
            assertEquals("REFUNDED", c.getStatus()); assertEquals(15000, c.getRefundedAmount());
            assertEquals(List.of("A_RESERVED", "B_PRINTED", "C_REFUNDED", "B_VOIDED", "A_RELEASED"), new WorkflowRepository(db.connection()).events(id));
        }
    }

    @Test void restartAfterAContinuesFromDiskWithoutReservingAgain() throws Exception {
        int id;
        try (Database db = new Database(store())) {
            var f = new TicketPurchasingFacade(db); id = f.startPurchase(100, 25, "RESTART").id();
            assertEquals(Workflow.Status.PENDING_B, f.advanceOne(id).status());
        }
        try (Database db = new Database(store())) {
            assertEquals(1, new TicketPurchasingAdminFacade(db).recoverAll().size());
            assertEquals(Workflow.Status.COMPLETED, new WorkflowRepository(db.connection()).find(id).orElseThrow().status());
            assertEquals(List.of("A_RESERVED", "B_PRINTED", "C_APPROVED"), new WorkflowRepository(db.connection()).events(id));
        }
    }

    @Test void restartDuringCompensationDoesNotRepeatSavedVoid() throws Exception {
        int id;
        try (Database db = new Database(store())) {
            var f = new TicketPurchasingFacade(db); id=f.startPurchase(100, 30, "UNDO").id();
            f.advanceOne(id); f.advanceOne(id);
            new TicketPurchasingAdminFacade(db).setGateOpen(100, false);
            assertEquals(Workflow.Status.COMPENSATING_B, f.advanceOne(id).status());
        }
        try (Database db = new Database(store())) {
            assertEquals(Workflow.Status.COMPENSATING_A, new TicketPurchasingFacade(db).advanceOne(id).status());
            assertEquals("VOIDED", new TicketProcessingRepository(db.connection()).find(id).orElseThrow().getStatus());
            assertEquals("RESERVED", new ReservationRepository(db.connection()).find(id).orElseThrow().getStatus());
        }
        try (Database db = new Database(store())) {
            var f = new TicketPurchasingFacade(db);
            assertEquals(Workflow.Status.COMPENSATED, f.resume(id).status());
            f.resume(id);
            assertEquals(1, new WorkflowRepository(db.connection()).events(id).stream().filter("B_VOIDED"::equals).count());
            assertEquals(1, new WorkflowRepository(db.connection()).events(id).stream().filter("A_RELEASED"::equals).count());
        }
    }

    @Test void failedProgressSaveRollsBackStageResultAndAuditTogether() throws Exception {
        int id;
        try (Database db = new Database(store())) {
            var f=new TicketPurchasingFacade(db); id=f.startPurchase(100,40,"ATOMIC").id();
            try(var s=db.connection().createStatement()) {
                s.execute("CREATE TRIGGER fail_progress BEFORE UPDATE ON workflows BEGIN SELECT RAISE(ABORT, 'disk write rejected'); END");
            }
            assertThrows(java.sql.SQLException.class, () -> f.advanceOne(id));
            assertTrue(new ReservationRepository(db.connection()).find(id).isEmpty());
            assertTrue(new WorkflowRepository(db.connection()).events(id).isEmpty());
            assertEquals(Workflow.Status.PENDING_A,new WorkflowRepository(db.connection()).find(id).orElseThrow().status());
            try(var s=db.connection().createStatement()) { s.execute("DROP TRIGGER fail_progress"); }
        }
        try(Database db=new Database(store())) { assertEquals(Workflow.Status.COMPLETED,new TicketPurchasingFacade(db).resume(id).status()); }
    }

    @Test void invalidInputAndOccupiedSeatCommitNoNewPurchase() throws Exception {
        try(Database db=new Database(store())) {
            var f=new TicketPurchasingFacade(db);
            assertThrows(BookingException.class, () -> f.buyTicket(0,1,"BAD"));
            assertThrows(BookingException.class, () -> f.buyTicket(100,101,"BAD"));
            assertThrows(BookingException.class, () -> f.buyTicket(100,1," "));
            assertTrue(new WorkflowRepository(db.connection()).all().isEmpty());
            concertgoerBuysSeat(f,50,"FIRST");
            assertThrows(BookingException.class, () -> concertgoerBuysSeat(f,50,"SECOND"));
            assertEquals(1,new WorkflowRepository(db.connection()).all().size());
            assertEquals(Workflow.Status.COMPLETED,f.buyTicket(200,50,"OTHER-EVENT").status());
        }
    }

    @Test void adminInspectsAndManuallyCompensatesOnlyUnfinishedPurchases() throws Exception {
        try(Database db=new Database(store())) {
            var f=new TicketPurchasingFacade(db); var a=new TicketPurchasingAdminFacade(db);
            var beforeA=f.startPurchase(100,60,"BEFORE-A");
            assertEquals(Workflow.Status.COMPENSATED,a.startCompensation(beforeA.id()).status());
            var afterA=f.startPurchase(100,61,"AFTER-A"); f.advanceOne(afterA.id());
            assertEquals(Workflow.Status.COMPENSATED,a.startCompensation(afterA.id()).status());
            assertEquals(Workflow.Status.COMPENSATED,a.startCompensation(afterA.id()).status());
            var afterB=f.startPurchase(100,62,"AFTER-B"); f.advanceOne(afterB.id()); f.advanceOne(afterB.id());
            assertEquals(Workflow.Status.COMPENSATED,a.startCompensation(afterB.id()).status());
            var complete=concertgoerBuysSeat(f,63,"COMPLETE");
            assertThrows(BookingException.class, () -> a.startCompensation(complete.id()));
            assertThrows(BookingException.class, () -> a.getPersistedState(99999));
            assertEquals(complete,a.getPersistedState(complete.id()));
            assertEquals(4,a.listPurchases().size());
            String snapshot=a.inspect();
            assertTrue(snapshot.contains("VOIDED")); assertTrue(snapshot.contains("RELEASED"));
            assertTrue(a.recoverAll().isEmpty());
        }
    }

    @Test void restartAfterBUsesPersistedInputForC() throws Exception {
        int id;
        try(Database db=new Database(store())) {
            var f=new TicketPurchasingFacade(db);id=f.startPurchase(100,70,"AFTER-B-RESTART").id();
            f.advanceOne(id);assertEquals(Workflow.Status.PENDING_C,f.advanceOne(id).status());
        }
        try(Database db=new Database(store())) {
            assertEquals(Workflow.Status.COMPLETED,new TicketPurchasingFacade(db).resume(id).status());
            assertEquals(List.of("A_RESERVED","B_PRINTED","C_APPROVED"),new WorkflowRepository(db.connection()).events(id));
        }
    }

    @ParameterizedTest
    @ValueSource(ints={0,1,2,3,4,5})
    void everyStageRollsBackItsStateAndProgressOnWriteFailure(int stage) throws Exception {
        int id;
        try(Database db=new Database(store())) {
            var f=new TicketPurchasingFacade(db); var admin=new TicketPurchasingAdminFacade(db);
            id=f.startPurchase(100,80,"ATOMIC-STAGE").id();
            for(int i=0;i<Math.min(stage,2);i++) f.advanceOne(id);
            if(stage>=3) admin.setGateOpen(100,false);
            if(stage==3 || stage==4) f.advanceOne(id);
            if(stage==4) f.advanceOne(id);
            String before=admin.inspect();
            try(var s=db.connection().createStatement()) {
                s.execute("CREATE TRIGGER fail_progress BEFORE UPDATE ON workflows BEGIN SELECT RAISE(ABORT,'write failed'); END");
            }
            assertThrows(java.sql.SQLException.class,()->f.advanceOne(id));
            assertEquals(before,admin.inspect(),"No subsystem result or audit entry may outlive its progress update");
            try(var s=db.connection().createStatement()) { s.execute("DROP TRIGGER fail_progress"); }
        }
        try(Database db=new Database(store())) {
            var w=new TicketPurchasingFacade(db).resume(id);
            assertEquals(stage>=3 ? Workflow.Status.COMPENSATED : Workflow.Status.COMPLETED,w.status());
        }
    }

    @Test void storageFailureDuringAvailabilityMustRemainRetryable() throws Exception {
        try(Database db=new Database(store())) {
            var f=new TicketPurchasingFacade(db);int id=f.startPurchase(100,90,"STORAGE-ERROR").id();
            try(var s=db.connection().createStatement()) { s.execute("ALTER TABLE seat_reservations RENAME TO unavailable_reservations"); }
            assertThrows(java.sql.SQLException.class,()->f.advanceOne(id));
            assertEquals(Workflow.Status.PENDING_A,new WorkflowRepository(db.connection()).find(id).orElseThrow().status());
            try(var s=db.connection().createStatement()) { s.execute("ALTER TABLE unavailable_reservations RENAME TO seat_reservations"); }
            assertEquals(Workflow.Status.COMPLETED,f.resume(id).status());
        }
    }

    @Test void directlyUsedSubsystemStateIsNotOverwrittenByPurchase() throws Exception {
        try(Database db=new Database(store())) {
            var reservations=new ReservationRepository(db.connection());
            reservations.save(new concert.reservation.SeatReservation(1,200,1,12000,"RESERVED"));
            var tickets=new TicketProcessingRepository(db.connection());
            tickets.save(new concert.processing.TicketProcessing(2,"STANDALONE","PRINTED"));
            var access=new TicketAccessRepository(db.connection());
            access.save(new concert.access.TicketAccess(3,"DIRECT",300,12000,"APPROVED",0));
            var w=new TicketPurchasingFacade(db).buyTicket(100,91,"FACADE");
            assertTrue(w.id()>3);
            assertEquals(200,reservations.find(1).orElseThrow().eventId);
            assertEquals("STANDALONE",tickets.find(2).orElseThrow().ticketCode);
            assertEquals("DIRECT",access.find(3).orElseThrow().ticketCode);
        }
    }

    @Test void releasedReservationCannotProduceANewTicketDuringRecovery() throws Exception {
        try(Database db=new Database(store())) {
            var f=new TicketPurchasingFacade(db);int id=f.startPurchase(100,92,"DIRECT-RELEASE").id();f.advanceOne(id);
            var repo=new ReservationRepository(db.connection());var a=repo.find(id).orElseThrow();a.release();repo.save(a);
            assertEquals(Workflow.Status.COMPENSATED,f.resume(id).status());
            assertTrue(new TicketProcessingRepository(db.connection()).find(id).isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"A","B"})
    void recoveryRefusesAccessIfAnEarlierSavedStageWasCancelledDirectly(String cancelled) throws Exception {
        try(Database db=new Database(store())) {
            var f=new TicketPurchasingFacade(db);int id=f.startPurchase(100,93,"DIRECT-CANCEL").id();
            f.advanceOne(id);f.advanceOne(id);
            var reservations=new ReservationRepository(db.connection());var tickets=new TicketProcessingRepository(db.connection());
            if(cancelled.equals("A")) { var a=reservations.find(id).orElseThrow();a.release();reservations.save(a); }
            else { var b=tickets.find(id).orElseThrow();b.voidTicket();tickets.save(b); }
            assertEquals(Workflow.Status.COMPENSATED,f.resume(id).status());
            assertEquals("RELEASED",reservations.find(id).orElseThrow().getStatus());
            assertEquals("VOIDED",tickets.find(id).orElseThrow().getStatus());
            assertEquals("REFUNDED",new TicketAccessRepository(db.connection()).find(id).orElseThrow().getStatus());
            assertFalse(new WorkflowRepository(db.connection()).events(id).contains("C_APPROVED"));
        }
    }
}
