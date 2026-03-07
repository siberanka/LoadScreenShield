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

public class LangManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private YamlConfiguration langConfig;

    public LangManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void loadLang() {
        String selectedLang = configManager.getLang();
        if (selectedLang == null || selectedLang.isEmpty()) {
            selectedLang = "en";
        }

        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        createDefaultLangFiles("en");
        createDefaultLangFiles("tr");

        File langFile = new File(langFolder, selectedLang + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning("Language file " + selectedLang + ".yml not found. Falling back to en.yml");
            langFile = new File(langFolder, "en.yml");
            if (!langFile.exists()) {
                createDefaultLangFiles("en");
            }
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);
        boolean needsSave = false;

        InputStream defaultStream = plugin.getResource("lang/" + selectedLang + ".yml");
        if (defaultStream == null) {
            defaultStream = plugin.getResource("lang/en.yml");
        }

        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration
                    .loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));

            for (String key : defaultConfig.getKeys(true)) {
                if (!langConfig.contains(key)) {
                    langConfig.set(key, defaultConfig.get(key));
                    needsSave = true;
                }
            }

            for (String key : langConfig.getKeys(true)) {
                if (!defaultConfig.contains(key)) {
                    langConfig.set(key, null);
                    needsSave = true;
                }
            }
        }

        if (needsSave) {
            try {
                langConfig.save(langFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save updated " + langFile.getName());
            }
        }
    }

    private void createDefaultLangFiles(String langCode) {
        File f = new File(plugin.getDataFolder() + File.separator + "lang", langCode + ".yml");
        if (!f.exists()) {
            plugin.saveResource("lang/" + langCode + ".yml", false);
        }
    }

    public Component getMessage(String key) {
        String msg = langConfig.getString(key, "<red>Missing lang key: " + key + "</red>");
        String prefix = configManager.getPrefix();
        return MiniMessage.miniMessage().deserialize(prefix + msg);
    }

    public Component getRawMessage(String key) {
        String msg = langConfig.getString(key, "<red>Missing lang key: " + key + "</red>");
        return MiniMessage.miniMessage().deserialize(msg);
    }
}
