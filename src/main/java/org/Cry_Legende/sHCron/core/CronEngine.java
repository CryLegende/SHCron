package org.Cry_Legende.sHCron.core;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        this.messages = new CronMessages(platform);
    }

    private final CronMessages messages;

    public CronMessages messages() {
        return messages;
    }

    public void start() {
        this.config = CronConfig.load(platform);
        try {
            messages.use(messages.read());
        } catch (ConfigError e) {
            platform.error("messages.yml illisible, textes par defaut utilises", e);
        }
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

    /**
     * Relit config.yml et messages.yml. Les deux sont d'abord lus à part : si l'un est illisible, la configuration
     * en place continue de tourner.
     *
     * @return une entrée « fichier\nerreur » par fichier illisible, vide si tout a été rechargé
     */
    public synchronized List<String> reload() {
        List<String> errors = new ArrayList<>();
        CronConfig fresh = null;
        Map<String, String> freshMessages = null;
        try {
            fresh = CronConfig.read(platform);
        } catch (ConfigError e) {
            platform.error(e.getMessage(), e);
            errors.add(e.file() + "\n" + e.reason());
        }
        try {
            freshMessages = messages.read();
        } catch (ConfigError e) {
            platform.error(e.getMessage(), e);
            errors.add(e.file() + "\n" + e.reason());
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        this.config = fresh;
        messages.use(freshMessages);
        lastMinute.set(currentMinute());
        announce();
        return errors;
    }

    private void announce() {
        long active = config.jobs().stream().filter(CronJob::enabled).count();
        platform.info("SHCron sur " + platform.name() + " : " + active + " tache(s) active(s) sur "
            + config.jobs().size() + ", fuseau " + config.zone() + ".");
        for (CronJob job : config.jobs()) {
            platform.info("  " + (job.enabled() ? "[on] " : "[off] ") + job.name()
                + "  cron=\"" + job.scheduleText() + "\"  " + job.commands().size() + " commande(s)");
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
                if (job.enabled() && !job.manual() && job.schedule().matches(now)) {
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

            Condition condition = Condition.parse(command, MAX_WAIT_SECONDS);
            if (condition != null) {
                int next = i + 1;
                if (next >= commands.size()) {
                    platform.warn("[" + jobName + "] attente finale sans commande apres : ignoree.");
                    return;
                }
                if (config.logExecutions()) {
                    platform.info("[" + jobName + "] attente de \"" + condition + "\" (" + condition.timeoutSeconds() + " s au plus)");
                }
                awaitCondition(jobName, commands, next, condition, System.currentTimeMillis() + condition.timeoutSeconds() * 1000L);
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
     * Relit la condition chaque seconde. La liste reprend des qu'elle est vraie, ou a la fin du delai :
     * une condition jamais remplie ne doit pas laisser la tache en suspens.
     */
    private void awaitCondition(String jobName, List<String> commands, int next, Condition condition, long deadline) {
        delayer().schedule(() -> {
            try {
                platform.resolve(condition.left()).thenCombine(platform.resolve(condition.right()), condition::test)
                    .whenComplete((met, error) -> {
                        try {
                            if (error != null) {
                                platform.error("[" + jobName + "] echec de lecture de \"" + condition + "\"", error);
                            }
                            if (Boolean.TRUE.equals(met)) {
                                runFrom(jobName, commands, next);
                            } else if (System.currentTimeMillis() >= deadline) {
                                platform.warn("[" + jobName + "] \"" + condition + "\" toujours fausse apres "
                                    + condition.timeoutSeconds() + " s : la suite part quand meme.");
                                runFrom(jobName, commands, next);
                            } else {
                                awaitCondition(jobName, commands, next, condition, deadline);
                            }
                        } catch (Exception e) {
                            platform.error("[" + jobName + "] echec apres l'attente", e);
                        }
                    });
            } catch (Exception e) {
                platform.error("[" + jobName + "] echec pendant l'attente", e);
            }
        }, 1, TimeUnit.SECONDS);
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
