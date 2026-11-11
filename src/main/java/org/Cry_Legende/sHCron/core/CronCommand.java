package org.Cry_Legende.sHCron.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /shcron list|reload|run, commun à Paper et à Velocity. */
public final class CronCommand {

    /** Destinataire d'une réponse et ses droits, adaptés par chaque plateforme. */
    public interface Sender {
        /** Envoie un texte en codes &. */
        void send(String legacyText);

        boolean has(String permission);
    }

    private static final String ADMIN = "shcron.admin";
    private static final List<String> SUBCOMMANDS = List.of("list", "reload", "run");

    private final CronEngine engine;

    public CronCommand(CronEngine engine) {
        this.engine = engine;
    }

    private static boolean can(Sender sender, String sub) {
        return sender.has(ADMIN + "." + sub) || sender.has(ADMIN);
    }

    private static List<String> allowed(Sender sender) {
        return SUBCOMMANDS.stream().filter(sub -> can(sender, sub)).toList();
    }

    /** Vrai si l'expéditeur a au moins un droit sur /shcron (sinon la commande n'a pas à lui apparaître). */
    public static boolean canUse(Sender sender) {
        return !allowed(sender).isEmpty();
    }

    public void execute(Sender sender, String[] args) {
        CronMessages messages = engine.messages();
        List<String> allowed = allowed(sender);
        if (allowed.isEmpty()) {
            sender.send(messages.get("no-permission"));
            return;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!allowed.contains(sub)) {
            sender.send(SUBCOMMANDS.contains(sub) ? messages.get("no-permission")
                : messages.get("usage", "%subcommands%", String.join("|", allowed)));
            return;
        }
        switch (sub) {
            case "list" -> list(sender);
            case "reload" -> reload(sender);
            default -> run(sender, args);
        }
    }

    private void list(Sender sender) {
        CronMessages messages = engine.messages();
        CronConfig config = engine.config();
        sender.send(messages.get("list-header", "%zone%", String.valueOf(config.zone())));
        if (config.jobs().isEmpty()) {
            sender.send(messages.get("list-empty"));
        }
        for (CronJob job : config.jobs()) {
            sender.send(messages.get("list-entry", "%state%", job.enabled() ? "&a●" : "&c●", "%name%", job.name(),
                "%schedule%", String.valueOf(job.schedule()), "%commands%", String.valueOf(job.commands().size())));
        }
    }

    private void reload(Sender sender) {
        List<String> errors = engine.reload();
        CronMessages messages = engine.messages();
        if (!errors.isEmpty()) {
            for (String error : errors) {
                int split = error.indexOf('\n');
                sender.send(messages.get("reload-error", "%file%", error.substring(0, split), "%error%", error.substring(split + 1)));
            }
            return;
        }
        CronConfig config = engine.config();
        long active = config.jobs().stream().filter(CronJob::enabled).count();
        sender.send(messages.get("reload-ok", "%active%", String.valueOf(active), "%total%", String.valueOf(config.jobs().size())));
        for (String warning : config.warnings()) {
            sender.send(messages.get("reload-warning", "%detail%", warning));
        }
    }

    private void run(Sender sender, String[] args) {
        CronMessages messages = engine.messages();
        if (args.length < 2) {
            sender.send(messages.get("run-usage"));
            return;
        }
        sender.send(engine.runNow(args[1]) ? messages.get("run-ok", "%name%", args[1]) : messages.get("run-missing", "%name%", args[1]));
    }

    /** Propositions pour le dernier argument saisi (args n'est jamais vide). */
    public List<String> suggest(Sender sender, String[] args) {
        List<String> allowed = allowed(sender);
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.addAll(allowed);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("run") && allowed.contains("run")) {
            engine.config().jobs().forEach(job -> suggestions.add(job.name()));
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        return suggestions.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}
