package com.siberanka.loadscreenshield.manager;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;

final class FloodgateAdapter {

    private final JavaPlugin plugin;
    private final Object api;
    private final Method playerCheck;
    private boolean warned;

    private FloodgateAdapter(JavaPlugin plugin, Object api, Method playerCheck) {
        this.plugin = plugin;
        this.api = api;
        this.playerCheck = playerCheck;
    }

    static FloodgateAdapter discover(JavaPlugin plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            return new FloodgateAdapter(plugin, null, null);
        }
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi", false,
                    plugin.getClass().getClassLoader());
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method check;
            try {
                check = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            } catch (NoSuchMethodException ignored) {
                check = apiClass.getMethod("isFloodgateId", UUID.class);
            }
            return new FloodgateAdapter(plugin, api, check);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Floodgate is enabled but its player API could not be linked; Java shielding remains enabled.", exception);
            return new FloodgateAdapter(plugin, null, null);
        }
    }

    boolean isFloodgatePlayer(UUID playerId) {
        if (api == null || playerCheck == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(playerCheck.invoke(api, playerId));
        } catch (IllegalAccessException | InvocationTargetException exception) {
            if (!warned) {
                warned = true;
                plugin.getLogger().log(Level.WARNING,
                        "Floodgate player detection failed; shielding this player as a safe fallback.", exception);
            }
            return false;
        }
    }
}
