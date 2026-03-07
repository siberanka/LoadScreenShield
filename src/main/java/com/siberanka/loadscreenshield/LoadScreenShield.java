package com.siberanka.loadscreenshield;

import com.siberanka.loadscreenshield.listener.EventListener;
import com.siberanka.loadscreenshield.manager.ConfigManager;
import com.siberanka.loadscreenshield.manager.LangManager;
import com.siberanka.loadscreenshield.manager.ShieldManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

import com.github.retrooper.packetevents.PacketEvents;

public class LoadScreenShield extends JavaPlugin implements CommandExecutor, TabCompleter {

    private ConfigManager configManager;
    private LangManager langManager;
    private ShieldManager shieldManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.langManager = new LangManager(this, configManager);
        this.shieldManager = new ShieldManager(this, configManager, langManager);

        configManager.loadConfig();
        langManager.loadLang();

        getServer().getPluginManager().registerEvents(new EventListener(this, shieldManager), this);

        getCommand("loadscreenshield").setExecutor(this);
        getCommand("loadscreenshield").setTabCompleter(this);

        getServer().getConsoleSender().sendMessage(langManager.getMessage("plugin-enabled"));
    }

    @Override
    public void onDisable() {
        if (shieldManager != null) {
            shieldManager.cleanupAll();
        }
        if (langManager != null) {
            getServer().getConsoleSender().sendMessage(langManager.getMessage("plugin-disabled"));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("loadscreenshield.admin")) {
                sender.sendMessage(langManager.getMessage("no-permission"));
                return true;
            }

            configManager.loadConfig();
            langManager.loadLang();
            sender.sendMessage(langManager.getMessage("reload-success"));
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("loadscreenshield.admin")) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }

    public LangManager getLangManager() {
        return langManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
