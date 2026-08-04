package com.siberanka.loadscreenshield.manager;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class ConfigManager {

    private static final Pattern SAFE_LANGUAGE = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final int CURRENT_SCHEMA_VERSION = 3;
    private static final int MIN_TIMEOUT_SECONDS = 5;
    private static final int MAX_TIMEOUT_SECONDS = 1800;
    private static final int MAX_BOX_RADIUS = 4;
    private static final int MAX_PREFIX_LENGTH = 1024;

    private final JavaPlugin plugin;
    private final File configFile;
    private volatile Snapshot snapshot = Snapshot.defaults();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    public synchronized void loadConfig() {
        boolean existedBeforeLoad = configFile.isFile();
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        try {
            String templateText = readBundledConfig();
            YamlConfiguration template = ConfigDocument.parseTemplate(templateText);
            ConfigDocument document = ConfigDocument.load(configFile.toPath(), existedBeforeLoad);
            YamlConfiguration loaded = document.configuration();

            if (document.loadProblem() != null) {
                plugin.getLogger().warning(document.loadProblem() + "; restoring safe documented defaults.");
            }

            ensureSupportedSchema(loaded, plugin.getLogger()::warning);

            Set<String> unknownKeys = document.unknownKeys(template);
            Set<String> missingKeys = document.missingKeys(template);
            if (!unknownKeys.isEmpty()) {
                plugin.getLogger().warning("Removing unknown config.yml entries after backup: "
                        + summarizeKeys(unknownKeys));
            }
            if (!missingKeys.isEmpty()) {
                plugin.getLogger().info("Adding missing documented config.yml entries: "
                        + summarizeKeys(missingKeys));
            }

            Snapshot validated = validate(loaded, plugin.getLogger()::warning);
            ConfigDocument.RewriteResult rewrite = document.rewriteCanonical(
                    templateText,
                    canonicalValues(validated)
            );
            if (rewrite.backup() != null) {
                plugin.getLogger().warning("Backed up the previous config.yml to "
                        + rewrite.backup().getFileName());
            }
            if (rewrite.changed()) {
                plugin.getLogger().info("Normalized config.yml to schema " + CURRENT_SCHEMA_VERSION
                        + " with documented settings in canonical order.");
            }
            snapshot = validated;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not safely normalize config.yml", exception);
        }
    }

    private String readBundledConfig() throws IOException {
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null) {
                throw new IOException("Bundled config.yml is missing from the plugin JAR");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String summarizeKeys(Set<String> keys) {
        int limit = 12;
        String summary = keys.stream().limit(limit).reduce((left, right) -> left + ", " + right).orElse("");
        if (keys.size() > limit) {
            summary += " (and " + (keys.size() - limit) + " more)";
        }
        return summary;
    }

    static void ensureSupportedSchema(YamlConfiguration loaded, Consumer<String> warning) {
        int schemaVersion = strictInt(loaded, "schema-version", 1, warning);
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("config.yml uses newer schema-version " + schemaVersion
                    + "; this plugin supports up to " + CURRENT_SCHEMA_VERSION
                    + ". Downgrade was stopped to avoid deleting future settings.");
        }
    }

    static Snapshot validate(YamlConfiguration loaded, Consumer<String> warning) {
        return validate(loaded, warning, ConfigManager::resolveBlockMaterial);
    }

    static Snapshot validate(
            YamlConfiguration loaded,
            Consumer<String> warning,
            Function<String, Material> blockMaterialResolver
    ) {
        String language = strictString(loaded, "lang", "en", MAX_PREFIX_LENGTH, warning).trim();
        if (!SAFE_LANGUAGE.matcher(language).matches()) {
            warning.accept("Invalid lang value; using en. Only letters, numbers, '_' and '-' are allowed.");
            language = "en";
        }

        String prefix = strictString(loaded, "prefix", "", MAX_PREFIX_LENGTH, warning);
        int timeoutSeconds = clamp(
                strictInt(loaded, "timeout-seconds", 120, warning),
                MIN_TIMEOUT_SECONDS,
                MAX_TIMEOUT_SECONDS,
                "timeout-seconds",
                warning
        );
        int boxRadius = clamp(
                strictInt(loaded, "shield-box-radius", 2, warning),
                1,
                MAX_BOX_RADIUS,
                "shield-box-radius",
                warning
        );

        String materialName = strictString(
                loaded, "shield-block-type", "BLACK_WOOL", 64, warning
        ).trim();
        Material shieldMaterial = blockMaterialResolver.apply(materialName.toUpperCase(Locale.ROOT));
        if (shieldMaterial == null) {
            warning.accept("Invalid shield-block-type '" + materialName + "'; using BLACK_WOOL.");
            shieldMaterial = Material.BLACK_WOOL;
        }

        ActivationMode activationMode = ActivationMode.parse(
                strictString(loaded, "activation-mode", ActivationMode.JOIN.name(), 64, warning),
                warning
        );

        return new Snapshot(
                language,
                prefix,
                timeoutSeconds,
                boxRadius,
                shieldMaterial,
                activationMode,
                strictBoolean(loaded, "title.enabled", true, warning),
                strictBoolean(loaded, "protection.enabled", true, warning),
                strictBoolean(loaded, "protection.cancel-movement", true, warning),
                strictBoolean(loaded, "protection.cancel-commands", true, warning),
                strictBoolean(loaded, "protection.cancel-teleports", true, warning),
                strictBoolean(loaded, "bedrock.ignore-floodgate", true, warning)
        );
    }

    private static Material resolveBlockMaterial(String materialName) {
        Material material = Material.matchMaterial(materialName);
        return material != null && material.isBlock() ? material : null;
    }

    static Map<String, Object> canonicalValues(Snapshot value) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("schema-version", CURRENT_SCHEMA_VERSION);
        values.put("lang", value.language());
        values.put("prefix", value.prefix());
        values.put("activation-mode", value.activationMode().name());
        values.put("timeout-seconds", value.timeoutSeconds());
        values.put("shield-box-radius", value.boxRadius());
        values.put("shield-block-type", value.shieldMaterial().name());
        values.put("title.enabled", value.titleEnabled());
        values.put("bedrock.ignore-floodgate", value.ignoreFloodgatePlayers());
        values.put("protection.enabled", value.protectionEnabled());
        values.put("protection.cancel-movement", value.cancelMovement());
        values.put("protection.cancel-commands", value.cancelCommands());
        values.put("protection.cancel-teleports", value.cancelTeleports());
        return Map.copyOf(values);
    }

    private static String strictString(
            YamlConfiguration loaded,
            String path,
            String fallback,
            int maximumLength,
            Consumer<String> warning
    ) {
        if (!loaded.isString(path)) {
            if (loaded.contains(path)) {
                warning.accept(path + " must be a string; using the documented default.");
            }
            return fallback;
        }
        String value = loaded.getString(path, fallback);
        if (value.length() > maximumLength) {
            warning.accept(path + " exceeds " + maximumLength + " characters; using the documented default.");
            return fallback;
        }
        return value;
    }

    private static int strictInt(
            YamlConfiguration loaded,
            String path,
            int fallback,
            Consumer<String> warning
    ) {
        if (!loaded.isInt(path)) {
            if (loaded.contains(path)) {
                warning.accept(path + " must be an integer; using " + fallback + '.');
            }
            return fallback;
        }
        return loaded.getInt(path);
    }

    private static boolean strictBoolean(
            YamlConfiguration loaded,
            String path,
            boolean fallback,
            Consumer<String> warning
    ) {
        if (!loaded.isBoolean(path)) {
            if (loaded.contains(path)) {
                warning.accept(path + " must be true or false; using " + fallback + '.');
            }
            return fallback;
        }
        return loaded.getBoolean(path);
    }

    private static int clamp(
            int value,
            int minimum,
            int maximum,
            String path,
            Consumer<String> warning
    ) {
        int clamped = Math.max(minimum, Math.min(maximum, value));
        if (clamped != value) {
            warning.accept(path + " must be between " + minimum + " and " + maximum
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

        private static ActivationMode parse(String value, Consumer<String> warning) {
            if (value != null) {
                try {
                    return valueOf(value.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    // The warning below explains the safe fallback.
                }
            }
            warning.accept("Invalid activation-mode; using JOIN.");
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
