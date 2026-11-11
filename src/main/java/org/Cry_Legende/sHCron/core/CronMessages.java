package org.Cry_Legende.sHCron.core;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Textes de messages.yml (créé à partir du jar s'il manque). Une clé absente du fichier du serveur prend sa valeur
 * dans celui du jar. Les couleurs restent en codes &, chaque plateforme les convertit.
 */
public final class CronMessages {

    private final Platform platform;
    private final Map<String, String> bundled;
    private volatile Map<String, String> current;

    public CronMessages(Platform platform) {
        this.platform = platform;
        Map<String, String> defaults = new HashMap<>();
        try (InputStream in = CronMessages.class.getClassLoader().getResourceAsStream("messages.yml")) {
            if (in != null) {
                defaults = flatten(new Yaml().load(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            platform.warn("messages.yml du jar illisible : " + e);
        }
        this.bundled = defaults;
        this.current = defaults;
    }

    /** Lit messages.yml sans rien appliquer ; le fichier est créé à partir du jar s'il manque. */
    public Map<String, String> read() throws ConfigError {
        Path file = platform.dataFolder().resolve("messages.yml");
        try {
            Files.createDirectories(platform.dataFolder());
            if (!Files.exists(file)) {
                try (InputStream in = CronMessages.class.getClassLoader().getResourceAsStream("messages.yml")) {
                    if (in != null) {
                        Files.copy(in, file);
                    }
                }
            }
        } catch (Exception e) {
            throw new ConfigError("messages.yml", e);
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, String> read = new HashMap<>(bundled);
            read.putAll(flatten(new Yaml().load(reader)));
            return read;
        } catch (Exception e) {
            throw new ConfigError("messages.yml", e);
        }
    }

    public void use(Map<String, String> messages) {
        this.current = messages;
    }

    /** Texte (codes & non convertis) avec les variables remplacées, en paires « %nom% », « valeur ». */
    public String get(String key, String... placeholders) {
        String text = current.get(key);
        if (text == null) {
            return key;
        }
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace(placeholders[i], placeholders[i + 1]);
        }
        return text;
    }

    private static Map<String, String> flatten(Object loaded) {
        Map<String, String> out = new HashMap<>();
        if (loaded instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    out.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            }
        }
        return out;
    }
}
