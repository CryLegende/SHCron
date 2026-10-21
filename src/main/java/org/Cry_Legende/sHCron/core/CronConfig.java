package org.Cry_Legende.sHCron.core;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lecture de config.yml.
 */
public final class CronConfig {

    private final ZoneId zone;
    private final boolean logExecutions;
    private final boolean startupEnabled;
    private final int startupDelaySeconds;
    private final List<String> startupCommands;
    private final List<CronJob> jobs;

    private CronConfig(ZoneId zone, boolean logExecutions, boolean startupEnabled,
                       int startupDelaySeconds, List<String> startupCommands, List<CronJob> jobs) {
        this.zone = zone;
        this.logExecutions = logExecutions;
        this.startupEnabled = startupEnabled;
        this.startupDelaySeconds = startupDelaySeconds;
        this.startupCommands = List.copyOf(startupCommands);
        this.jobs = List.copyOf(jobs);
    }

    @SuppressWarnings("unchecked")
    public static CronConfig load(Platform platform) {
        Path folder = platform.dataFolder();
        Path file = folder.resolve("config.yml");
        try {
            Files.createDirectories(folder);
            if (!Files.exists(file)) {
                try (InputStream in = CronConfig.class.getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, file);
                        platform.info("config.yml cree avec les valeurs par defaut.");
                    }
                }
            }
        } catch (Exception e) {
            platform.error("Impossible de preparer config.yml", e);
        }

        Map<String, Object> root = Map.of();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            if (loaded instanceof Map<?, ?> m) {
                root = (Map<String, Object>) m;
            }
        } catch (Exception e) {
            platform.error("config.yml illisible, aucune tache ne sera planifiee", e);
            return new CronConfig(ZoneId.systemDefault(), true, false, 0, List.of(), List.of());
        }

        ZoneId zone;
        Object tz = root.get("timezone");
        if (tz == null || String.valueOf(tz).isBlank()) {
            zone = ZoneId.systemDefault();
        } else {
            try {
                zone = ZoneId.of(String.valueOf(tz));
            } catch (Exception e) {
                platform.warn("Fuseau horaire inconnu \"" + tz + "\", utilisation de " + ZoneId.systemDefault());
                zone = ZoneId.systemDefault();
            }
        }

        boolean logExec = bool(root, "log-executions", true);

        Map<String, Object> startup = section(root, "startup");
        boolean startupEnabled = bool(startup, "enabled", false);
        int startupDelay = integer(startup, "delay-seconds", 10);
        List<String> startupCommands = strings(startup.get("commands"));

        List<CronJob> jobs = new ArrayList<>();
        Map<String, Object> jobsSection = section(root, "jobs");
        for (Map.Entry<String, Object> entry : jobsSection.entrySet()) {
            String name = entry.getKey();
            if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                platform.warn("Tache \"" + name + "\" ignoree : bloc invalide.");
                continue;
            }
            Map<String, Object> job = (Map<String, Object>) raw;
            boolean enabled = bool(job, "enabled", true);
            Object cron = job.get("cron");
            if (cron == null) {
                platform.warn("Tache \"" + name + "\" ignoree : cle \"cron\" absente.");
                continue;
            }
            List<String> commands = strings(job.get("commands"));
            if (commands.isEmpty()) {
                platform.warn("Tache \"" + name + "\" ignoree : aucune commande.");
                continue;
            }
            try {
                jobs.add(new CronJob(name, enabled, CronExpression.parse(String.valueOf(cron)), commands));
            } catch (IllegalArgumentException e) {
                platform.warn("Tache \"" + name + "\" ignoree : " + e.getMessage());
            }
        }

        return new CronConfig(zone, logExec, startupEnabled, startupDelay, startupCommands, jobs);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String key) {
        Object v = root.get(key);
        return (v instanceof Map<?, ?> m) ? (Map<String, Object>) m : Map.of();
    }

    private static boolean bool(Map<String, Object> map, String key, boolean def) {
        Object v = map.get(key);
        return (v instanceof Boolean b) ? b : def;
    }

    private static int integer(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        return (v instanceof Number n) ? n.intValue() : def;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o != null) {
                String s = String.valueOf(o).trim();
                // on tolere que l'utilisateur ecrive "/end" comme dans le jeu
                if (s.startsWith("/")) {
                    s = s.substring(1);
                }
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    public ZoneId zone() {
        return zone;
    }

    public boolean logExecutions() {
        return logExecutions;
    }

    public boolean startupEnabled() {
        return startupEnabled;
    }

    public int startupDelaySeconds() {
        return startupDelaySeconds;
    }

    public List<String> startupCommands() {
        return startupCommands;
    }

    public List<CronJob> jobs() {
        return jobs;
    }
}
