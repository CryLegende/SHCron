package org.Cry_Legende.sHCron.bukkit;

import org.Cry_Legende.sHCron.core.CronEngine;
import org.Cry_Legende.sHCron.core.CronJob;
import org.Cry_Legende.sHCron.core.Platform;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SHCronBukkit extends JavaPlugin implements Platform, TabExecutor {

    private CronEngine engine;
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
        var command = getCommand("shcron");
        if (command != null) {
            command.setExecutor(this);
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
     * Resolution en contexte serveur (joueur null), suffisante pour %voteparty_...%.
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

    private static void send(CommandSender to, String message) {
        to.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shcron.admin")) {
            send(sender, "&8&l« &c&lSH &8&l» &7Commande inconnue, essayer autre chose.");
            return true;
        }
        if (args.length == 0) {
            send(sender, "&8&l« &b&lSHCRON &8&l» &7Syntaxe : &b/shcron <list|reload|run>");
            return true;
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
                    return true;
                }
                if (engine.runNow(args[1])) {
                    send(sender, "&8&l« &b&lSHCRON &8&l» &7Tache &b" + args[1] + " &7declenchee.");
                } else {
                    send(sender, "&8&l« &c&lSHCRON &8&l» &7Tache &c" + args[1] + " &7introuvable.");
                }
            }
            default -> send(sender, "&8&l« &b&lSHCRON &8&l» &7Syntaxe : &b/shcron <list|reload|run>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shcron.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("list", "reload", "run").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                .toList();
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
}
