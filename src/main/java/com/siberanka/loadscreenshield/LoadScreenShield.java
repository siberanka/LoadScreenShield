package com.siberanka.loadscreenshield;

import com.siberanka.loadscreenshield.listener.EventListener;
import com.siberanka.loadscreenshield.manager.ConfigManager;
import com.siberanka.loadscreenshield.manager.LangManager;
import com.siberanka.loadscreenshield.manager.ShieldManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public final class LoadScreenShield extends JavaPlugin implements CommandExecutor, TabCompleter {

    private ConfigManager configManager;
    private LangManager langManager;
    private ShieldManager shieldManager;
    private final AtomicBoolean reloadInProgress = new AtomicBoolean();

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        langManager = new LangManager(this, configManager);
        configManager.loadConfig();
        langManager.loadLang();

        shieldManager = new ShieldManager(this, configManager, langManager);
        getServer().getPluginManager().registerEvents(new EventListener(this, shieldManager), this);

        PluginCommand pluginCommand = getCommand("loadscreenshield");
        if (pluginCommand == null) {
            throw new IllegalStateException("The loadscreenshield command is missing from plugin.yml");
        }
        pluginCommand.setExecutor(this);
        pluginCommand.setTabCompleter(this);

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
        if (args.length == 1 && "reload".equals(args[0].toLowerCase(Locale.ROOT))) {
            if (!sender.hasPermission("loadscreenshield.admin")) {
                sender.sendMessage(langManager.getMessage("no-permission"));
                return true;
            }

            if (!reloadInProgress.compareAndSet(false, true)) {
                sender.sendMessage(langManager.getMessage("reload-in-progress"));
                return true;
            }

            getServer().getAsyncScheduler().runNow(this, task -> {
                boolean success = false;
                try {
                    configManager.loadConfig();
                    langManager.loadLang();
                    success = true;
                } catch (RuntimeException exception) {
                    getLogger().log(Level.SEVERE, "Configuration reload failed", exception);
                }

                boolean reloadSucceeded = success;
                Runnable finish = () -> {
                    reloadInProgress.set(false);
                    if (!isEnabled()) {
                        return;
                    }
                    if (reloadSucceeded) {
                        shieldManager.applyConfiguration();
                    }
                    sender.sendMessage(langManager.getMessage(
                            reloadSucceeded ? "reload-success" : "reload-failed"
                    ));
                };
                if (sender instanceof Player player) {
                    player.getScheduler().execute(this, finish, () -> reloadInProgress.set(false), 1L);
                } else {
                    getServer().getGlobalRegionScheduler().execute(this, finish);
                }
            });
            return true;
        }

        sender.sendMessage(langManager.getMessage("command-usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("loadscreenshield.admin")
                && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LangManager getLangManager() {
        return langManager;
    }
}
