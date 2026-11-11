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
import org.Cry_Legende.sHCron.core.CronCommand;
import org.Cry_Legende.sHCron.core.CronEngine;
import org.Cry_Legende.sHCron.core.Platform;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Plugin(
    id = "shcron",
    name = "SHCron",
    version = "1.3",
    description = "Planificateur de commandes",
    authors = {"Cry_Legende"}
)
public final class SHCronVelocity implements Platform {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataFolder;

    private CronEngine engine;
    private CronCommand cronCommand;
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
        cronCommand = new CronCommand(engine);
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

        private CronCommand.Sender wrap(CommandSource source) {
            return new CronCommand.Sender() {
                @Override
                public void send(String legacyText) {
                    source.sendMessage(LEGACY.deserialize(legacyText));
                }

                @Override
                public boolean has(String permission) {
                    return source.hasPermission(permission);
                }
            };
        }

        @Override
        public void execute(Invocation invocation) {
            cronCommand.execute(wrap(invocation.source()), invocation.arguments());
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            String[] args = invocation.arguments();
            if (args.length == 0) {
                args = new String[]{""};
            }
            return cronCommand.suggest(wrap(invocation.source()), args);
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return CronCommand.canUse(wrap(invocation.source()));
        }
    }
}
