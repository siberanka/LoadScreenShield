package com.siberanka.loadscreenshield.manager;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ConfigManager {

    private static final Pattern SAFE_LANGUAGE = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final int MIN_TIMEOUT_SECONDS = 5;
    private static final int MAX_TIMEOUT_SECONDS = 1800;
    private static final int MAX_BOX_RADIUS = 4;

    private final JavaPlugin plugin;
    private final File configFile;
    private volatile Snapshot snapshot = Snapshot.defaults();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    public synchronized void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(configFile);
        boolean needsSave = mergeMissingDefaults(loaded);
        int schemaVersion = loaded.getInt("schema-version", 1);
        if (schemaVersion < 2) {
            loaded.set("schema-version", 2);
            needsSave = true;
        } else if (schemaVersion > 2) {
            plugin.getLogger().warning("config.yml uses newer schema-version " + schemaVersion
                    + "; unknown keys will be preserved, but downgrade compatibility is not guaranteed.");
        }

        String language = loaded.getString("lang", "en");
        if (!SAFE_LANGUAGE.matcher(language).matches()) {
            plugin.getLogger().warning("Invalid lang value; using en. Only letters, numbers, '_' and '-' are allowed.");
            language = "en";
        }

        int timeoutSeconds = clamp(
                loaded.getInt("timeout-seconds", 120),
                MIN_TIMEOUT_SECONDS,
                MAX_TIMEOUT_SECONDS,
                "timeout-seconds"
        );
        int boxRadius = clamp(loaded.getInt("shield-box-radius", 2), 1, MAX_BOX_RADIUS, "shield-box-radius");

        String materialName = loaded.getString("shield-block-type", "BLACK_WOOL");
        Material shieldMaterial = materialName == null
                ? null
                : Material.matchMaterial(materialName.toUpperCase(Locale.ROOT));
        if (shieldMaterial == null || !shieldMaterial.isBlock()) {
            plugin.getLogger().warning("Invalid shield-block-type '" + materialName + "'; using BLACK_WOOL.");
            shieldMaterial = Material.BLACK_WOOL;
        }

        ActivationMode activationMode = ActivationMode.parse(
                loaded.getString("activation-mode", ActivationMode.JOIN.name()),
                plugin
        );

        snapshot = new Snapshot(
                language,
                loaded.getString("prefix", ""),
                timeoutSeconds,
                boxRadius,
                shieldMaterial,
                activationMode,
                loaded.getBoolean("title.enabled", true),
                loaded.getBoolean("protection.enabled", true),
                loaded.getBoolean("protection.cancel-movement", true),
                loaded.getBoolean("protection.cancel-commands", true),
                loaded.getBoolean("protection.cancel-teleports", true),
                loaded.getBoolean("bedrock.ignore-floodgate", true)
        );

        if (needsSave) {
            try {
                loaded.save(configFile);
            } catch (IOException exception) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save updated config.yml", exception);
            }
        }
    }

    private boolean mergeMissingDefaults(YamlConfiguration loaded) {
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null) {
                return false;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!loaded.contains(key)) {
                    loaded.set(key, defaults.get(key));
                    changed = true;
                }
            }
            return changed;
        } catch (IOException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not read the default config", exception);
            return false;
        }
    }

    private int clamp(int value, int minimum, int maximum, String path) {
        int clamped = Math.max(minimum, Math.min(maximum, value));
        if (clamped != value) {
            plugin.getLogger().warning(path + " must be between " + minimum + " and " + maximum
                    + "; using " + clamped + '.');
        }
        return clamped;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public enum ActivationMode {
        JOIN,
        RESOURCE_PACK_STATUS;

        private static ActivationMode parse(String value, JavaPlugin plugin) {
            if (value != null) {
                try {
                    return valueOf(value.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // The warning below explains the safe fallback.
                }
            }
            plugin.getLogger().warning("Invalid activation-mode; using JOIN.");
            return JOIN;
        }
    }

    public record Snapshot(
            String language,
            String prefix,
            int timeoutSeconds,
            int boxRadius,
            Material shieldMaterial,
            ActivationMode activationMode,
            boolean titleEnabled,
            boolean protectionEnabled,
            boolean cancelMovement,
            boolean cancelCommands,
            boolean cancelTeleports,
            boolean ignoreFloodgatePlayers
    ) {
        private static Snapshot defaults() {
            return new Snapshot(
                    "en", "", 120, 2, Material.BLACK_WOOL, ActivationMode.JOIN,
                    true, true, true, true, true, true
            );
        }
    }
}
