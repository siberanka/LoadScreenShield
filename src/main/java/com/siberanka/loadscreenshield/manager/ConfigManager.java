package com.siberanka.loadscreenshield.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigManager {

    private final JavaPlugin plugin;
    private YamlConfiguration config;
    private final File configFile;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }

    public void loadConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        boolean needsSave = false;

        InputStream defaultStream = plugin.getResource("config.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration
                    .loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));

            // Add missing keys
            for (String key : defaultConfig.getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defaultConfig.get(key));
                    needsSave = true;
                }
            }

            // Remove extra keys
            for (String key : config.getKeys(true)) {
                if (!defaultConfig.contains(key)) {
                    config.set(key, null);
                    needsSave = true;
                }
            }
        }

        if (needsSave) {
            try {
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save updated config.yml!");
            }
        }
    }

    public String getString(String path) {
        return config.getString(path);
    }

    public int getInt(String path) {
        return config.getInt(path);
    }

    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    public String getLang() {
        return getString("lang");
    }

    public String getPrefix() {
        String p = getString("prefix");
        return p != null ? p : "";
    }
}
