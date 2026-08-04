package com.siberanka.loadscreenshield.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class LangManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile Messages messages = Messages.empty();

    public LangManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public synchronized void loadLang() {
        String selectedLanguage = configManager.snapshot().language();
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create the language directory; bundled defaults will be used.");
        }

        createDefaultLanguageFile("en");
        createDefaultLanguageFile("tr");

        File langFile = new File(langFolder, selectedLanguage + ".yml");
        String defaultsLanguage = selectedLanguage;
        if (!langFile.isFile()) {
            plugin.getLogger().warning("Language file " + selectedLanguage + ".yml was not found; using en.yml.");
            langFile = new File(langFolder, "en.yml");
            defaultsLanguage = "en";
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(langFile);
        boolean changed = mergeMissingDefaults(loaded, defaultsLanguage);
        if (changed) {
            try {
                loaded.save(langFile);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not save updated " + langFile.getName(), exception);
            }
        }

        Map<String, Component> raw = new HashMap<>();
        Map<String, Component> prefixed = new HashMap<>();
        String prefix = configManager.snapshot().prefix();
        for (String key : loaded.getKeys(true)) {
            if (!loaded.isString(key)) {
                continue;
            }
            String value = loaded.getString(key, "");
            raw.put(key, deserialize(value, key));
            prefixed.put(key, deserialize(prefix + value, key));
        }
        messages = new Messages(raw, prefixed);
    }

    private boolean mergeMissingDefaults(YamlConfiguration loaded, String language) {
        try (InputStream stream = plugin.getResource("lang/" + language + ".yml")) {
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
            plugin.getLogger().log(Level.WARNING, "Could not read bundled language defaults", exception);
            return false;
        }
    }

    private void createDefaultLanguageFile(String language) {
        File file = new File(new File(plugin.getDataFolder(), "lang"), language + ".yml");
        if (!file.exists()) {
            plugin.saveResource("lang/" + language + ".yml", false);
        }
    }

    private Component deserialize(String value, String key) {
        try {
            return miniMessage.deserialize(value);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Invalid MiniMessage in language key '" + key + "'; displaying plain text.");
            return Component.text(value);
        }
    }

    public Component getMessage(String key) {
        return messages.prefixed().getOrDefault(key, missing(key));
    }

    public Component getRawMessage(String key) {
        return messages.raw().getOrDefault(key, missing(key));
    }

    private Component missing(String key) {
        return Component.text("Missing lang key: " + key);
    }

    private record Messages(Map<String, Component> raw, Map<String, Component> prefixed) {
        private Messages {
            raw = Collections.unmodifiableMap(new HashMap<>(raw));
            prefixed = Collections.unmodifiableMap(new HashMap<>(prefixed));
        }

        private static Messages empty() {
            return new Messages(Collections.emptyMap(), Collections.emptyMap());
        }
    }
}
