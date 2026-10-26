package org.Cry_Legende.sHCron.core;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Moteur de planification. Interroge l'horloge une fois par seconde et declenche
 * les taches quand la minute courante change, ce qui evite de derive comme le
 * ferait un simple "toutes les N ticks".
 */
public final class CronEngine {

    /**
     * Directive de pause au milieu d'une liste de commandes.
     * Accepte "wait: 5", "wait 5", "delay: 5", "delay 5".
     * Remplace la commande /timer de MC-Cron, dont l'unite etait la seconde.
     */
    private static final Pattern WAIT = Pattern.compile(
        "^(?:wait|delay)\\s*:?\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);

    /**
     * Decalage absolu depuis le debut de la tache : "+30 tm abroadcast ...".
     * La commande part 30 s apres le declenchement, et la liste continue
     * immediatement — plusieurs "+N" se declenchent donc en parallele.
     * <p>
     * Reproduit exactement "timer N commande" de SRV-Cron. A ne pas melanger
     * avec "wait:" dans une meme tache : celui-ci est sequentiel et cumulatif.
     * DOTALL parce qu'une commande peut tenir sur plusieurs lignes.
     */
    private static final Pattern OFFSET = Pattern.compile(
        "^\\+(\\d+)\\s+(.+)$", Pattern.DOTALL);

    /**
     * MC-Cron plafonnait /timer a 300 s ; on garde une borne du meme ordre.
     */
    private static final long MAX_WAIT_SECONDS = 3600;

    private final Platform platform;
    // Ecrits par le thread principal (start, reload), lus par le thread du tick
    private volatile CronConfig config;

    /**
     * Minute epoch deja traitee, pour ne jamais executer deux fois la meme.
     */
    private final AtomicLong lastMinute = new AtomicLong(-1);
    private volatile long startupDueAt = -1;

    /**
     * Sert uniquement a reprendre une liste de commandes apres une pause.
     */
    private ScheduledExecutorService delayer;

    public CronEngine(Platform platform) {
        this.platform = platform;
    }

    public void start() {
        this.config = CronConfig.load(platform);
        announce();

        if (config.startupEnabled() && !config.startupCommands().isEmpty()) {
            startupDueAt = System.currentTimeMillis() + config.startupDelaySeconds() * 1000L;
        }

        // On considere la minute en cours comme deja traitee : un redemarrage a
        // 03:00:30 ne doit pas rejouer la tache de 03:00.
        lastMinute.set(currentMinute());
        platform.startTicking(this::tick);
    }

    public synchronized void stop() {
        platform.stopTicking();
        if (delayer != null) {
            delayer.shutdownNow();
            delayer = null;
        }
    }

    private synchronized ScheduledExecutorService delayer() {
        if (delayer == null) {
            delayer = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SHCron-delay");
                t.setDaemon(true);
                return t;
            });
        }
        return delayer;
    }

    public CronConfig config() {
        return config;
    }

    public void reload() {
        this.config = CronConfig.load(platform);
        lastMinute.set(currentMinute());
        announce();
    }

    private void announce() {
        long active = config.jobs().stream().filter(CronJob::enabled).count();
        platform.info("SHCron sur " + platform.name() + " : " + active + " tache(s) active(s) sur "
            + config.jobs().size() + ", fuseau " + config.zone() + ".");
        for (CronJob job : config.jobs()) {
            platform.info("  " + (job.enabled() ? "[on] " : "[off] ") + job.name()
                + "  cron=\"" + job.schedule() + "\"  " + job.commands().size() + " commande(s)");
        }
    }

    private long currentMinute() {
        return System.currentTimeMillis() / 60_000L;
    }

    private void tick() {
        try {
            if (startupDueAt > 0 && System.currentTimeMillis() >= startupDueAt) {
                startupDueAt = -1;
                platform.info("Commandes de demarrage :");
                run("startup", config.startupCommands());
            }

            long minute = currentMinute();
            long previous = lastMinute.get();
            if (minute == previous || !lastMinute.compareAndSet(previous, minute)) {
                return;
            }

            // Heure déduite de la minute comptée : un tick à cheval sur deux minutes ne peut pas lancer deux fois la même tâche
            CronConfig current = config;
            ZonedDateTime now = Instant.ofEpochSecond(minute * 60L).atZone(current.zone());
            for (CronJob job : current.jobs()) {
                if (job.enabled() && job.schedule().matches(now)) {
                    run(job.name(), job.commands());
                }
            }
        } catch (Exception e) {
            platform.error("Erreur pendant le tick SHCron", e);
        }
    }

    /**
     * Execute une tache a la demande (commande /shcron run).
     */
    public boolean runNow(String jobName) {
        Optional<CronJob> job = config.jobs().stream()
            .filter(j -> j.name().equalsIgnoreCase(jobName))
            .findFirst();
        job.ifPresent(j -> run(j.name(), j.commands()));
        return job.isPresent();
    }

    private void run(String jobName, List<String> commands) {
        runFrom(jobName, commands, 0);
    }

    /**
     * Execute les commandes dans l'ordre. Une directive "wait: N" suspend la
     * suite pendant N secondes sans bloquer le thread appelant : le reste de la
     * liste est reprogramme.
     */
    private void runFrom(String jobName, List<String> commands, int from) {
        for (int i = from; i < commands.size(); i++) {
            String command = commands.get(i);

            // Decalage absolu : on programme cette seule commande et on continue
            // la liste tout de suite, sans decaler les suivantes.
            Matcher off = OFFSET.matcher(command.trim());
            if (off.matches()) {
                long seconds = seconds(off.group(1));
                String actual = off.group(2).trim();
                if (actual.startsWith("/")) {
                    actual = actual.substring(1);
                }
                if (config.logExecutions()) {
                    platform.info("[" + jobName + "] +" + seconds + "s > " + firstLine(actual));
                }
                final String toRun = actual;
                delayer().schedule(() -> {
                    try {
                        platform.dispatchConsole(toRun);
                    } catch (Exception e) {
                        platform.error("[" + jobName + "] echec de \"" + firstLine(toRun) + "\"", e);
                    }
                }, seconds, TimeUnit.SECONDS);
                continue;
            }

            Matcher m = WAIT.matcher(command.trim());
            if (m.matches()) {
                long seconds = seconds(m.group(1));
                int next = i + 1;
                if (next >= commands.size()) {
                    platform.warn("[" + jobName + "] pause finale sans commande apres : ignoree.");
                    return;
                }
                if (config.logExecutions()) {
                    platform.info("[" + jobName + "] pause de " + seconds + " s");
                }
                delayer().schedule(() -> {
                    try {
                        runFrom(jobName, commands, next);
                    } catch (Exception e) {
                        platform.error("[" + jobName + "] echec apres la pause", e);
                    }
                }, seconds, TimeUnit.SECONDS);
                return;
            }

            if (config.logExecutions()) {
                platform.info("[" + jobName + "] > " + firstLine(command));
            }
            try {
                platform.dispatchConsole(command);
            } catch (Exception e) {
                platform.error("[" + jobName + "] echec de \"" + firstLine(command) + "\"", e);
            }
        }
    }

    /**
     * Délai lu dans une directive, borné ; un nombre démesuré ne doit pas faire avorter toute la tâche.
     */
    private static long seconds(String digits) {
        try {
            return Math.min(Long.parseLong(digits), MAX_WAIT_SECONDS);
        } catch (NumberFormatException e) {
            return MAX_WAIT_SECONDS;
        }
    }

    /**
     * Les commandes peuvent tenir sur plusieurs lignes ; on n'en journalise qu'une.
     */
    private static String firstLine(String command) {
        int nl = command.indexOf('\n');
        return nl < 0 ? command : command.substring(0, nl) + " [...]";
    }

    /**
     * Utilise par la validation de config pour signaler une directive isolee.
     */
    public static boolean isWaitDirective(String command) {
        return WAIT.matcher(command.trim()).matches();
    }

    /**
     * Vrai si la commande porte un decalage "+N ".
     */
    public static boolean isOffsetDirective(String command) {
        return OFFSET.matcher(command.trim()).matches();
    }
}
