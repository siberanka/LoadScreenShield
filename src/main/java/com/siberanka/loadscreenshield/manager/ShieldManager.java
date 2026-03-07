package com.siberanka.loadscreenshield.manager;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import com.github.retrooper.packetevents.protocol.world.states.type.StateType;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.util.Vector3i;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShieldManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final LangManager langManager;

    // Players currently shielded
    private final Set<UUID> currentlyShielded = ConcurrentHashMap.newKeySet();

    // Players who have ever entered the shield state in this server session
    private final Set<UUID> shieldedEverSession = ConcurrentHashMap.newKeySet();

    private final boolean isFolia;

    public ShieldManager(JavaPlugin plugin, ConfigManager configManager, LangManager langManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.langManager = langManager;

        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
        }
        this.isFolia = folia;
    }

    public void activateShield(Player player) {
        if (shieldedEverSession.contains(player.getUniqueId())) {
            return;
        }

        // Floodgate Check: Bedrock players do not download Java resource packs the same
        // way
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            if (org.geysermc.floodgate.api.FloodgateApi.getInstance().isFloodgateId(player.getUniqueId())) {
                return;
            }
        }

        currentlyShielded.add(player.getUniqueId());
        shieldedEverSession.add(player.getUniqueId());

        Component msg = langManager.getMessage("shield-activated");
        player.sendMessage(msg);

        if (configManager.getBoolean("title.enabled", true)) {
            // Repeat the title every second (handled by scheduleRepeatingTaskTask)
            scheduleRepeatingTaskTask(player.getLocation(), () -> {
                if (player.isOnline() && isShielded(player)) {
                    Component titleMain = langManager.getMessage("title-main");
                    Component titleSub = langManager.getMessage("title-sub");
                    player.showTitle(net.kyori.adventure.title.Title.title(titleMain, titleSub));
                }
            });
        }

        // Apply blindness and fake blocks
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 60 * 10, 10, false, false, false));

        sendFakeBox(player);

        // Schedule timeout
        long timeoutSeconds = configManager.getInt("timeout-seconds");
        if (timeoutSeconds <= 0)
            timeoutSeconds = 120;

        scheduleDelayedTask(player.getLocation(), () -> {
            Player p = Bukkit.getPlayer(player.getUniqueId());
            if (p != null && isShielded(p)) {
                deactivateShield(p, langManager.getMessage("shield-timeout"));
            }
        }, timeoutSeconds * 20L);
    }

    public void deactivateShield(Player player, Component reasonMessage) {
        if (!currentlyShielded.contains(player.getUniqueId())) {
            return;
        }

        currentlyShielded.remove(player.getUniqueId());

        if (reasonMessage != null) {
            player.sendMessage(reasonMessage);
        }

        player.clearTitle();

        player.clearTitle();

        player.removePotionEffect(PotionEffectType.BLINDNESS);

        // Remove fake blocks by sending real blocks
        removeFakeBox(player);
    }

    private void sendFakeBox(Player player) {
        String blockTypeStr = configManager.getString("shield-block-type");
        Material mat = Material.BLACK_WOOL;
        if (blockTypeStr != null) {
            try {
                mat = Material.valueOf(blockTypeStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        StateType stateType = StateTypes.getByName("minecraft:" + mat.name().toLowerCase());
        if (stateType == null)
            stateType = StateTypes.BLACK_WOOL;

        WrappedBlockState wrappedState = stateType.createBlockState();
        Location center = player.getLocation().getBlock().getLocation();

        // Send 5x5x5 block packets
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Vector3i pos = new Vector3i(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(pos,
                            wrappedState.getGlobalId());
                    PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
                }
            }
        }
    }

    private void removeFakeBox(Player player) {
        Location center = player.getLocation().getBlock().getLocation();
        // Send real blocks back to the client using PacketEvents
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Location loc = center.clone().add(x, y, z);

                    com.github.retrooper.packetevents.protocol.world.states.type.StateType stateType = com.github.retrooper.packetevents.protocol.world.states.type.StateTypes
                            .getByName("minecraft:" + loc.getBlock().getType().name().toLowerCase());

                    if (stateType == null)
                        stateType = com.github.retrooper.packetevents.protocol.world.states.type.StateTypes.AIR;

                    WrappedBlockState wrappedState = stateType.createBlockState();

                    Vector3i pos = new Vector3i(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(pos,
                            wrappedState.getGlobalId());
                    PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
                }
            }
        }
    }

    public boolean isShielded(Player player) {
        return currentlyShielded.contains(player.getUniqueId());
    }

    public void cleanupPlayer(Player player) {
        if (currentlyShielded.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.clearTitle();
        }
    }

    public void cleanupAll() {
        currentlyShielded.clear();
    }

    private void scheduleDelayedTask(Location loc, Runnable task, long delayTicks) {
        if (isFolia) {
            Bukkit.getRegionScheduler().runDelayed(plugin, loc, t -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    private void scheduleRepeatingTaskTask(Location loc, Runnable task) {
        if (isFolia) {
            Bukkit.getRegionScheduler().runAtFixedRate(plugin, loc, t -> {
                task.run();
                if (!t.getOwningPlugin().isEnabled() || t.isCancelled())
                    t.cancel();
            }, 1L, 20L); // 1 tick delay, 20 tick period
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, 1L, 20L);
        }
    }
}
