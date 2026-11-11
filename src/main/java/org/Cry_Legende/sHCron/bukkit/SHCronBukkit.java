package org.Cry_Legende.sHCron.bukkit;

import org.Cry_Legende.sHCron.core.CronCommand;
import org.Cry_Legende.sHCron.core.CronEngine;
import org.Cry_Legende.sHCron.core.Platform;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.List;

public final class SHCronBukkit extends JavaPlugin implements Platform, TabExecutor {

    private CronEngine engine;
    private CronCommand cronCommand;
    private BukkitTask task;

    /**
     * PlaceholderAPI.setPlaceholders(OfflinePlayer, String), resolu par reflection.
     * Null si PlaceholderAPI n'est pas installe : aucune dependance dure, et la
     * classe n'est jamais chargee dans ce cas.
     */
    private java.lang.reflect.Method papiSetPlaceholders;

    @Override
    public void onEnable() {
        hookPlaceholderApi();
        engine = new CronEngine(this);
        engine.start();
        cronCommand = new CronCommand(engine);
        var command = getCommand("shcron");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    private void hookPlaceholderApi() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            papiSetPlaceholders = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                .getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class);
            getLogger().info("PlaceholderAPI detecte : les placeholders des commandes seront resolus.");
        } catch (Throwable t) {
            getLogger().warning("PlaceholderAPI present mais inutilisable : " + t);
        }
    }

    /**
     * Resolution en contexte serveur (joueur null), suffisante pour %shvotesreward_...%.
     */
    private String applyPlaceholders(String command) {
        if (papiSetPlaceholders == null || command.indexOf('%') < 0) {
            return command;
        }
        try {
            Object out = papiSetPlaceholders.invoke(null, null, command);
            return (out instanceof String s) ? s : command;
        } catch (Throwable t) {
            getLogger().warning("Echec de resolution des placeholders : " + t);
            return command;
        }
    }

    @Override
    public void onDisable() {
        if (engine != null) {
            engine.stop();
        }
    }

    // ---------------------------------------------------------------- Platform

    @Override
    public Path dataFolder() {
        return getDataFolder().toPath();
    }

    @Override
    public void info(String message) {
        getLogger().info(message);
    }

    @Override
    public void warn(String message) {
        getLogger().warning(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        getLogger().log(java.util.logging.Level.SEVERE, message, throwable);
    }

    @Override
    public void dispatchConsole(String command) {
        // Le tick tourne en async : toute commande doit repasser par le thread principal.
        // Les placeholders y sont résolus aussi : beaucoup d'extensions PlaceholderAPI touchent à l'API Bukkit.
        if (!isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(this,
            () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), applyPlaceholders(command)));
    }

    @Override
    public void startTicking(Runnable tick) {
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(this, tick, 20L, 20L);
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
        return getServer().getName();
    }

    // ----------------------------------------------------------------- Command

    private static CronCommand.Sender wrap(CommandSender sender) {
        return new CronCommand.Sender() {
            @Override
            public void send(String legacyText) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', legacyText));
            }

            @Override
            public boolean has(String permission) {
                return sender.hasPermission(permission);
            }
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        cronCommand.execute(wrap(sender), args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return cronCommand.suggest(wrap(sender), args);
    }
}
