package com.siberanka.loadscreenshield.manager;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

public final class ShieldManager {

    private static final int BLINDNESS_REFRESH_TICKS = 60;
    private static final Title.Times TITLE_TIMES = Title.Times.times(
            Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO
    );

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final LangManager langManager;
    private final FloodgateAdapter floodgate;
    private final ConcurrentMap<UUID, ShieldSession> sessions = new ConcurrentHashMap<>();

    public ShieldManager(JavaPlugin plugin, ConfigManager configManager, LangManager langManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.langManager = langManager;
        this.floodgate = FloodgateAdapter.discover(plugin);
    }

    public void handleJoin(Player player) {
        ConfigManager.Snapshot config = configManager.snapshot();
        if (!config.protectionEnabled() || config.activationMode() != ConfigManager.ActivationMode.JOIN) {
            return;
        }
        player.getScheduler().runDelayed(plugin, task -> activateJoinShield(player), null, 1L);
    }

    private void activateJoinShield(Player player) {
        PlayerResourcePackStatusEvent.Status latestStatus = player.getResourcePackStatus();
        String statusName = latestStatus == null ? null : latestStatus.name();
        if (!ResourcePackStatusPolicy.shouldActivateJoinShield(statusName)) {
            return;
        }
        activateShield(player, true);
    }

    public void handleResourcePackStatus(Player player, UUID packId, String statusName) {
        ResourcePackStatusPolicy.Outcome outcome = ResourcePackStatusPolicy.classify(statusName);
        ShieldSession session = sessions.get(player.getUniqueId());

        if ((outcome == ResourcePackStatusPolicy.Outcome.WAITING
                || outcome == ResourcePackStatusPolicy.Outcome.UNKNOWN) && session == null) {
            session = activateShield(player, false);
        }
        if (session == null) {
            return;
        }

        boolean terminal = session.packTracker().record(packId, outcome);
        if (!terminal) {
            return;
        }

        if (outcome == ResourcePackStatusPolicy.Outcome.SUCCESS) {
            deactivateShield(player, langManager.getMessage("shield-deactivated"));
        } else {
            String messageKey = "DECLINED".equalsIgnoreCase(statusName) ? "shield-declined" : "shield-failed";
            deactivateShield(player, langManager.getMessage(messageKey));
        }
    }

    private ShieldSession activateShield(Player player, boolean waitingForInitialStatus) {
        ConfigManager.Snapshot config = configManager.snapshot();
        if (!config.protectionEnabled() || !player.isOnline()) {
            return null;
        }
        if (config.ignoreFloodgatePlayers() && floodgate.isFloodgatePlayer(player.getUniqueId())) {
            return null;
        }

        ShieldSession existing = sessions.get(player.getUniqueId());
        if (existing != null) {
            return existing;
        }

        List<Location> overlayLocations = createOverlayLocations(player, config.boxRadius());
        ShieldSession created = new ShieldSession(
                new ResourcePackTracker(waitingForInitialStatus), overlayLocations
        );
        ShieldSession raced = sessions.putIfAbsent(player.getUniqueId(), created);
        if (raced != null) {
            return raced;
        }

        player.sendMessage(langManager.getMessage("shield-activated"));
        refreshVisuals(player, created, config);
        sendFakeBox(player, created.overlayLocations(), config.shieldMaterial().createBlockData());

        ScheduledTask repeatingTask = player.getScheduler().runAtFixedRate(plugin, task -> {
            ShieldSession current = sessions.get(player.getUniqueId());
            if (current != created || !player.isOnline()) {
                task.cancel();
                return;
            }
            refreshVisuals(player, created, configManager.snapshot());
        }, () -> sessions.remove(player.getUniqueId(), created), 20L, 20L);
        created.setRepeatingTask(repeatingTask);

        ScheduledTask timeoutTask = player.getScheduler().runDelayed(plugin, task -> {
            if (sessions.get(player.getUniqueId()) == created) {
                deactivateShield(player, langManager.getMessage("shield-timeout"));
            }
        }, () -> sessions.remove(player.getUniqueId(), created), config.timeoutSeconds() * 20L);
        created.setTimeoutTask(timeoutTask);
        if (sessions.get(player.getUniqueId()) != created) {
            created.cancelTasks();
        }
        return created;
    }

    private void refreshVisuals(Player player, ShieldSession session, ConfigManager.Snapshot config) {
        if (sessions.get(player.getUniqueId()) != session) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.BLINDNESS, BLINDNESS_REFRESH_TICKS, 1, false, false, false
        ));
        if (config.titleEnabled()) {
            player.showTitle(Title.title(
                    langManager.getRawMessage("title-main"),
                    langManager.getRawMessage("title-sub"),
                    TITLE_TIMES
            ));
        }
    }

    public void deactivateShield(Player player, Component reasonMessage) {
        ShieldSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.cancelTasks();
        player.clearTitle();
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        restoreFakeBox(player, session.overlayLocations());
        if (reasonMessage != null) {
            player.sendMessage(reasonMessage);
        }
    }

    private List<Location> createOverlayLocations(Player player, int radius) {
        Location center = player.getLocation().getBlock().getLocation();
        World world = center.getWorld();
        int minimumY = world.getMinHeight();
        int maximumY = world.getMaxHeight() - 1;
        List<Location> locations = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1) * (radius * 2 + 1));
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                int blockY = center.getBlockY() + y;
                if (blockY < minimumY || blockY > maximumY) {
                    continue;
                }
                for (int z = -radius; z <= radius; z++) {
                    locations.add(new Location(
                            world,
                            center.getBlockX() + x,
                            blockY,
                            center.getBlockZ() + z
                    ));
                }
            }
        }
        return List.copyOf(locations);
    }

    private void sendFakeBox(Player player, List<Location> locations, BlockData fakeBlock) {
        try {
            // Paper and any protocol translator encode these server-owned states for the client version.
            for (Location location : locations) {
                player.sendBlockChange(location, fakeBlock);
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not send the client-side shield blocks to " + player.getName()
                            + "; blindness and event protection remain active.", exception);
        }
    }

    private void restoreFakeBox(Player player, List<Location> locations) {
        World playerWorld = player.getWorld();
        try {
            for (Location location : locations) {
                if (location.getWorld() == playerWorld) {
                    player.sendBlockChange(location, location.getBlock().getBlockData());
                }
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE,
                    "The client-side shield blocks could not be restored for " + player.getName(), exception);
        }
    }

    public boolean isShielded(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void cleanupPlayer(Player player) {
        ShieldSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.cancelTasks();
        }
    }

    public void cleanupAll() {
        for (Map.Entry<UUID, ShieldSession> entry : List.copyOf(sessions.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            ShieldSession session = entry.getValue();
            session.cancelTasks();
            if (player == null || !player.isOnline()) {
                sessions.remove(entry.getKey(), session);
                continue;
            }
            try {
                player.getScheduler().execute(plugin, () -> {
                    if (sessions.remove(entry.getKey(), session)) {
                        player.clearTitle();
                        player.removePotionEffect(PotionEffectType.BLINDNESS);
                        restoreFakeBox(player, session.overlayLocations());
                    }
                }, () -> sessions.remove(entry.getKey(), session), 1L);
            } catch (IllegalStateException ignored) {
                // The scheduler can reject new work after disable begins. Short blindness expires naturally.
                sessions.remove(entry.getKey(), session);
            }
        }
    }

    public void applyConfiguration() {
        if (!configManager.snapshot().protectionEnabled()) {
            for (UUID playerId : List.copyOf(sessions.keySet())) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.getScheduler().execute(plugin,
                            () -> deactivateShield(player, null),
                            () -> sessions.remove(playerId),
                            1L);
                } else {
                    sessions.remove(playerId);
                }
            }
        }
    }

    private static final class ShieldSession {
        private final ResourcePackTracker packTracker;
        private final List<Location> overlayLocations;
        private volatile ScheduledTask repeatingTask;
        private volatile ScheduledTask timeoutTask;

        private ShieldSession(ResourcePackTracker packTracker, List<Location> overlayLocations) {
            this.packTracker = packTracker;
            this.overlayLocations = overlayLocations;
        }

        private ResourcePackTracker packTracker() {
            return packTracker;
        }

        private List<Location> overlayLocations() {
            return overlayLocations;
        }

        private void setRepeatingTask(ScheduledTask repeatingTask) {
            this.repeatingTask = repeatingTask;
        }

        private void setTimeoutTask(ScheduledTask timeoutTask) {
            this.timeoutTask = timeoutTask;
        }

        private void cancelTasks() {
            ScheduledTask repeating = repeatingTask;
            if (repeating != null) {
                repeating.cancel();
            }
            ScheduledTask timeout = timeoutTask;
            if (timeout != null) {
                timeout.cancel();
            }
        }
    }
}
