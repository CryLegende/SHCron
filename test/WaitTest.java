import org.Cry_Legende.shcron.core.CronEngine;
import org.Cry_Legende.shcron.core.Platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Verifie que "wait: N" suspend la suite sans bloquer, et dans le bon ordre. */
public class WaitTest {

    static final List<String> executed = new ArrayList<>();
    static final List<Long> times = new ArrayList<>();
    static final CountDownLatch done = new CountDownLatch(2);
    static long start;

    static class FakePlatform implements Platform {
        final Path dir;
        FakePlatform(Path dir) { this.dir = dir; }
        public Path dataFolder() { return dir; }
        public void info(String m) { System.out.println("  [info] " + m); }
        public void warn(String m) { System.out.println("  [warn] " + m); }
        public void error(String m, Throwable t) { System.out.println("  [err ] " + m + " / " + t); }
        public void dispatchConsole(String command) {
            synchronized (executed) {
                executed.add(command);
                times.add(System.currentTimeMillis() - start);
            }
            done.countDown();
        }
        public void startTicking(Runnable tick) { /* pas de tick dans ce test */ }
        public void stopTicking() { }
        public String name() { return "Test"; }
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("shcron-test");
        Files.writeString(dir.resolve("config.yml"), """
                timezone: "Europe/Paris"
                log-executions: true
                startup:
                  enabled: false
                  delay-seconds: 0
                  commands: []
                jobs:
                  redemarrage:
                    enabled: true
                    cron: "0 3 * * *"
                    commands:
                      - "kickall Redemarrage..."
                      - "wait: 2"
                      - "stop"
                  sansPause:
                    enabled: true
                    cron: "@daily"
                    commands:
                      - "un"
                      - "deux"
                """);

        CronEngine engine = new CronEngine(new FakePlatform(dir));
        engine.start();

        int fails = 0;

        // --- la directive est bien reconnue ---
        for (String ok : List.of("wait: 2", "wait 2", "delay: 30", "DELAY:5")) {
            if (!CronEngine.isWaitDirective(ok)) { System.out.println("  ECHEC: '" + ok + "' devrait etre une pause"); fails++; }
        }
        for (String no : List.of("stop", "wait", "waiting 2", "wait: abc", "kickall wait: 2")) {
            if (CronEngine.isWaitDirective(no)) { System.out.println("  ECHEC: '" + no + "' ne devrait PAS etre une pause"); fails++; }
        }

        // --- sequencement reel ---
        start = System.currentTimeMillis();
        engine.runNow("redemarrage");

        // La 1re commande doit etre partie tout de suite, la 2e pas encore.
        Thread.sleep(400);
        synchronized (executed) {
            if (executed.size() != 1 || !executed.get(0).equals("kickall Redemarrage...")) {
                System.out.println("  ECHEC: 1re commande = " + executed); fails++;
            }
        }

        if (!done.await(6, TimeUnit.SECONDS)) {
            System.out.println("  ECHEC: la commande apres la pause n'est jamais partie"); fails++;
        }

        synchronized (executed) {
            if (executed.size() != 2 || !executed.get(1).equals("stop")) {
                System.out.println("  ECHEC: ordre final = " + executed); fails++;
            } else {
                long delta = times.get(1) - times.get(0);
                System.out.println("  pause mesuree : " + delta + " ms (2000 attendu)");
                if (delta < 1800 || delta > 3500) {
                    System.out.println("  ECHEC: pause hors tolerance"); fails++;
                }
            }
        }

        // --- une tache sans pause reste synchrone ---
        executed.clear();
        engine.runNow("sansPause");
        synchronized (executed) {
            if (!executed.equals(List.of("un", "deux"))) {
                System.out.println("  ECHEC: tache sans pause = " + executed); fails++;
            }
        }

        engine.stop();
        System.out.println(fails == 0 ? "TOUS LES TESTS OK" : fails + " ECHEC(S)");
        System.exit(fails == 0 ? 0 : 1);
    }
}
