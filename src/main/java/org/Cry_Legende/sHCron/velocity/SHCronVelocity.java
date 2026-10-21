package org.Cry_Legende.sHCron.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.Cry_Legende.sHCron.core.CronEngine;
import org.Cry_Legende.sHCron.core.CronJob;
import org.Cry_Legende.sHCron.core.Platform;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Plugin(
    id = "shcron",
    name = "SHCron",
    version = "1.2",
    description = "Planificateur de commandes",
    authors = {"Cry_Legende"}
)
public final class SHCronVelocity implements Platform {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataFolder;

    private CronEngine engine;
    private ScheduledTask task;

    @Inject
    public SHCronVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataFolder) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataFolder = dataFolder;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        engine = new CronEngine(this);
        engine.start();
        proxy.getCommandManager().register(
            proxy.getCommandManager().metaBuilder("shcron").plugin(this).build(),
            new ShCronCommand());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (engine != null) {
            engine.stop();
        }
    }

    // ---------------------------------------------------------------- Platform

    @Override
    public Path dataFolder() {
        return dataFolder;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    @Override
    public void dispatchConsole(String command) {
        proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), command);
    }

    @Override
    public void startTicking(Runnable tick) {
        task = proxy.getScheduler().buildTask(this, tick)
            .delay(Duration.ofSeconds(1))
            .repeat(Duration.ofSeconds(1))
            .schedule();
    }

    @Override
    public void stopTicking() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public String name() {
        return "Velocity";
    }

    // ----------------------------------------------------------------- Command

    private final class ShCronCommand implements SimpleCommand {

        private void send(CommandSource to, String message) {
            to.sendMessage(LEGACY.deserialize(message));
        }

        @Override
        public void execute(Invocation invocation) {
            CommandSource sender = invocation.source();
            String[] args = invocation.arguments();

            if (args.length == 0) {
                send(sender, "&8&l« &b&lSHCRON &8&l» &7Syntaxe : &b/shcron <list|reload|run>");
                return;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "list" -> {
                    send(sender, "&8&l« &b&lSHCRON &8&l» &7Taches (&bfuseau " + engine.config().zone() + "&7) :");
                    if (engine.config().jobs().isEmpty()) {
                        send(sender, "&7  aucune tache configuree.");
                    }
                    for (CronJob job : engine.config().jobs()) {
                        send(sender, "&7  " + (job.enabled() ? "&a●" : "&c●") + " &f" + job.name()
                            + " &8| &b" + job.schedule() + " &8| &7" + job.commands().size() + " cmd");
                    }
                }
                case "reload" -> {
                    engine.reload();
                    send(sender, "&8&l« &b&lSHCRON &8&l» &7Configuration &brechargee&7.");
                }
                case "run" -> {
                    if (args.length < 2) {
                        send(sender, "&8&l« &b&lSHCRON &8&l» &7Syntaxe : &b/shcron run <tache>");
                        return;
                    }
                    if (engine.runNow(args[1])) {
                        send(sender, "&8&l« &b&lSHCRON &8&l» &7Tache &b" + args[1] + " &7declenchee.");
                    } else {
                        send(sender, "&8&l« &c&lSHCRON &8&l» &7Tache &c" + args[1] + " &7introuvable.");
                    }
                }
                default -> send(sender, "&8&l« &b&lSHCRON &8&l» &7Syntaxe : &b/shcron <list|reload|run>");
            }
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            String[] args = invocation.arguments();
            if (args.length <= 1) {
                String q = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                return List.of("list", "reload", "run").stream().filter(s -> s.startsWith(q)).toList();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("run")) {
                List<String> names = new ArrayList<>();
                for (CronJob job : engine.config().jobs()) {
                    if (job.name().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        names.add(job.name());
                    }
                }
                return names;
            }
            return List.of();
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("shcron.admin");
        }
    }
}
