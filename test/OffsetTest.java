import org.Cry_Legende.shcron.core.CronEngine;
import org.Cry_Legende.shcron.core.Platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Verifie que "+N commande" reproduit "timer N commande" de SRV-Cron :
 * decalage absolu depuis le debut de la tache, en parallele.
 */
public class OffsetTest {

    static final List<String> got = new ArrayList<>();
    static final List<Long> at = new ArrayList<>();
    static long start;

    static class FakePlatform implements Platform {
        final Path dir;
        FakePlatform(Path dir) { this.dir = dir; }
        public Path dataFolder() { return dir; }
        public void info(String m) { }
        public void warn(String m) { System.out.println("  [warn] " + m); }
        public void error(String m, Throwable t) { System.out.println("  [err] " + m + " / " + t); }
        public void dispatchConsole(String c) {
            synchronized (got) { got.add(c); at.add(System.currentTimeMillis() - start); }
        }
        public void startTicking(Runnable tick) { }
        public void stopTicking() { }
        public String name() { return "Test"; }
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("shcron-offset");
        Files.writeString(dir.resolve("config.yml"), """
                timezone: "Europe/Paris"
                log-executions: false
                startup:
                  enabled: false
                  delay-seconds: 0
                  commands: []
                jobs:
                  restart:
                    enabled: true
                    cron: "0 3 * * *"
                    commands:
                      - "bc immediat"
                      - "+1 bc a-une-seconde"
                      - "+3 bc a-trois-secondes"
                      - "+2 bc a-deux-secondes"
                      - "bc immediat-aussi"
                  multi:
                    enabled: true
                    cron: "@daily"
                    commands:
                      - |-
                        bc premiere ligne
                        seconde ligne
                """);

        CronEngine engine = new CronEngine(new FakePlatform(dir));
        engine.start();
        int fails = 0;

        for (String ok : List.of("+1 bc x", "+30 tm abroadcast y", "+0 stop")) {
            if (!CronEngine.isOffsetDirective(ok)) { System.out.println("  ECHEC: '" + ok + "' devrait etre un offset"); fails++; }
        }
        for (String no : List.of("bc +1 x", "+abc stop", "+5", "stop")) {
            if (CronEngine.isOffsetDirective(no)) { System.out.println("  ECHEC: '" + no + "' ne devrait PAS etre un offset"); fails++; }
        }

        start = System.currentTimeMillis();
        engine.runNow("restart");

        // les deux commandes sans offset doivent etre parties tout de suite,
        // et la liste ne doit PAS avoir ete bloquee par les "+N"
        Thread.sleep(400);
        synchronized (got) {
            if (!got.equals(List.of("bc immediat", "bc immediat-aussi"))) {
                System.out.println("  ECHEC: immediat = " + got); fails++;
            }
        }

        TimeUnit.SECONDS.sleep(4);
        synchronized (got) {
            List<String> want = List.of("bc immediat", "bc immediat-aussi",
                    "bc a-une-seconde", "bc a-deux-secondes", "bc a-trois-secondes");
            if (!got.equals(want)) {
                System.out.println("  ECHEC: ordre final = " + got); fails++;
            } else {
                // offsets absolus, pas cumulatifs : 1s, 2s, 3s et non 1s, 3s, 6s
                long t1 = at.get(2), t2 = at.get(3), t3 = at.get(4);
                System.out.printf("  offsets mesures : %d / %d / %d ms (1000 / 2000 / 3000 attendus)%n", t1, t2, t3);
                if (Math.abs(t1 - 1000) > 400 || Math.abs(t2 - 2000) > 400 || Math.abs(t3 - 3000) > 400) {
                    System.out.println("  ECHEC: offsets hors tolerance (cumulatifs au lieu d'absolus ?)"); fails++;
                }
            }
        }

        // commande multi-lignes
        got.clear();
        engine.runNow("multi");
        synchronized (got) {
            if (got.size() != 1 || !got.get(0).contains("\n")) {
                System.out.println("  ECHEC: multi-lignes = " + got); fails++;
            } else {
                System.out.println("  multi-lignes preservee : " + got.get(0).replace("\n", " / "));
            }
        }

        engine.stop();
        System.out.println(fails == 0 ? "TOUS LES TESTS OK" : fails + " ECHEC(S)");
        System.exit(fails == 0 ? 0 : 1);
    }
}
