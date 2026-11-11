package org.Cry_Legende.sHCron.core;

/** Un fichier de configuration illisible : son nom et la première ligne de l'erreur. */
public final class ConfigError extends Exception {

    private final String file;
    private final String reason;

    public ConfigError(String file, Throwable cause) {
        super(file + " : " + cause.getMessage(), cause);
        this.file = file;
        String text = String.valueOf(cause.getMessage()).strip();
        int newline = text.indexOf('\n');
        this.reason = newline < 0 ? text : text.substring(0, newline);
    }

    public String file() {
        return file;
    }

    public String reason() {
        return reason;
    }
}
