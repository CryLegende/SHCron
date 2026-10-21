package org.Cry_Legende.sHCron.core;

import java.nio.file.Path;

/**
 * Tout ce dont le moteur a besoin de la plateforme hote.
 * Une implementation pour Paper/Purpur, une pour Velocity.
 */
public interface Platform {

    Path dataFolder();

    void info(String message);

    void warn(String message);

    void error(String message, Throwable throwable);

    /**
     * Execute une commande en tant que console.
     * L'implementation est responsable du bon thread (thread principal sur Bukkit).
     */
    void dispatchConsole(String command);

    /**
     * Appelle {@code tick} une fois par seconde.
     */
    void startTicking(Runnable tick);

    void stopTicking();

    /**
     * "Paper" ou "Velocity", pour les logs.
     */
    String name();
}
