package concert;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Scanner;

/** CLI is a client: it talks only to the two facades. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        var path = Arrays.stream(args)
                .filter(a -> !a.startsWith("--"))
                .findFirst()
                .orElse("concert.db");
        boolean recover = !Arrays.asList(args).contains("--no-recover");

        try (Database db = new Database(Path.of(path));
             Scanner input = new Scanner(System.in)) {
            var purchasing = new TicketPurchasingFacade(db);
            var admin = new TicketPurchasingAdminFacade(db);
            System.out.println("Concert Ticketing | SQLite: " + Path.of(path).toAbsolutePath());

            if (recover) {
                try {
                    for (var w : admin.recoverAll()) {
                        System.out.println("Recovered: " + w);
                    }
                } catch (SQLException | BookingException e) {
                    System.out.println("Recovery pending; retry option 4: " + e.getMessage());
                }
            }

            while (true) {
                System.out.println("\n1 Buy | 2 Inspect | 3 Gate open/close | 4 Recover | 5 Compensate | 6 Pause after A | 7 One stage | 0 Exit");
                if (!input.hasNextLine()) {
                    return;
                }
                String option = input.nextLine().trim();

                try {
                    switch (option) {
                        case "0" -> {
                            return;
                        }
                        case "1", "6" -> {
                            int event = number(input, "Event id (>0):");
                            int seat = number(input, "Seat (1..100):");
                            String code = line(input, "Ticket code (reuse a saved code to cause B refusal):");
                            Workflow w;
                            if (option.equals("1")) {
                                w = purchasing.buyTicket(event, seat, code);
                            } else {
                                w = purchasing.startPurchase(event, seat, code);
                                w = purchasing.advanceOne(w.id());
                            }
                            System.out.println(w);
                        }
                        case "2" -> System.out.print(admin.inspect());
                        case "3" -> {
                            int event = number(input, "Event id:");
                            String state = line(input, "Open=1, closed=0:");
                            if (!state.equals("0") && !state.equals("1")) {
                                throw new BookingException("Enter 1 or 0");
                            }
                            admin.setGateOpen(event, state.equals("1"));
                            System.out.println("Gate saved: " + state);
                        }
                        case "4" -> {
                            for (var w : admin.recoverAll()) {
                                System.out.println(w);
                            }
                        }
                        case "5" -> System.out.println(admin.startCompensation(number(input, "Workflow id:")));
                        case "7" -> System.out.println(purchasing.advanceOne(number(input, "Workflow id:")));
                        default -> System.out.println("Choose a listed option");
                    }
                } catch (SQLException e) {
                    System.out.println("Storage error; last committed progress is preserved: " + e.getMessage());
                } catch (BookingException | IllegalArgumentException e) {
                    System.out.println("Rejected: " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("Cannot open SQLite store: " + e.getMessage());
        }
    }

    private static String line(Scanner input, String prompt) {
        System.out.println(prompt);
        if (!input.hasNextLine()) {
            throw new BookingException("Input ended");
        }
        return input.nextLine().trim();
    }

    private static int number(Scanner input, String prompt) {
        return Integer.parseInt(line(input, prompt));
    }
}
